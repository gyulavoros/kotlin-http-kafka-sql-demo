package co.makery.kotlin

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.Uri
import akka.http.javadsl.model.headers.ETag
import akka.japi.Pair
import akka.stream.ActorMaterializer
import akka.stream.DelayOverflowStrategy
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import org.slf4j.LoggerFactory
import scala.concurrent.duration.FiniteDuration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

class GitHubClient(system: ActorSystem, private val materializer: ActorMaterializer) {

  private data class GitHubRequest(
    val eTagOpt: Optional<String> = Optional.empty(),
    val delayOpt: Optional<Long> = Optional.empty()
  )

  private data class GitHubResponse(
    val eTagOpt: Optional<String>,
    val pollIntervalOpt: Optional<Long>,
    val nodesOpt: Optional<ArrayNode>
  )

  private val logger = LoggerFactory.getLogger(javaClass)

  private val client = Http.get(system)

  private var lastPollInterval = 0L

  private fun mapToHttpRequest(request: GitHubRequest): HttpRequest {
    val headers = mutableListOf<HttpHeader>().apply {
      request.eTagOpt.map { tag ->
        add(HttpHeader.parse("If-None-Match", tag))
      }
    }
    val uri = Uri.create("https://api.github.com/orgs/kotlin/events")
    return HttpRequest.create()
      .withUri(uri)
      .withHeaders(headers)
  }

  private fun mapToGitHubResponse(response: HttpResponse): CompletionStage<GitHubResponse> {
    val eTagOpt = response.getHeader(ETag::class.java).map { header ->
      "\"${header.etag().tag()}\""
    }
    val pollIntervalOpt = response.getHeader("X-Poll-Interval").map { header ->
      lastPollInterval = header.value().toLong() // in case of 304 Not Modified GitHub doesn't return X-Poll-Interval
      lastPollInterval
    }
    val gitHubResponse = GitHubResponse(
      eTagOpt,
      pollIntervalOpt,
      Optional.empty()
    )
    return when (response.status().intValue()) {
      in 200..299 -> arrayUnmarshaller
        .unmarshal(response.entity(), materializer)
        .thenApply { nodes ->
          gitHubResponse.copy(nodesOpt = Optional.of(nodes))
        }
      else -> CompletableFuture.completedFuture(gitHubResponse)
    }
  }

  private fun executeWithDelay(request: GitHubRequest): CompletionStage<GitHubResponse> {
    fun execute(request: GitHubRequest): CompletionStage<GitHubResponse> {
      val httpRequest = mapToHttpRequest(request)
      logger.debug("executing request: $httpRequest")
      return client.singleRequest(httpRequest).thenCompose { response -> mapToGitHubResponse(response) }
    }
    return Source
      .single(request)
      .delay(FiniteDuration(request.delayOpt.orElse(lastPollInterval), TimeUnit.SECONDS), DelayOverflowStrategy.backpressure())
      .mapAsync(1, ::execute)
      .runWith(Sink.head(), materializer)
  }

  private fun poll(): Source<GitHubResponse, NotUsed> =
    Source.unfoldAsync(GitHubRequest(), { request ->
      executeWithDelay(request).thenApply { response ->
        val nextRequest = GitHubRequest(eTagOpt = response.eTagOpt, delayOpt = response.pollIntervalOpt)
        Optional.of(Pair.create(nextRequest, response))
      }
    })

  fun events(): Source<JsonNode, NotUsed> =
    poll().flatMapConcat { response ->
      response.nodesOpt
        .map { nodes -> Source.from(nodes) }
        .orElse(Source.empty())
    }
}
