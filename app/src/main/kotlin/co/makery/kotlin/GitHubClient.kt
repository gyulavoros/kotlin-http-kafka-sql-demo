package co.makery.kotlin

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.headers.ETag
import akka.japi.Pair
import akka.stream.ActorMaterializer
import akka.stream.DelayOverflowStrategy
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import scala.concurrent.duration.FiniteDuration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

class GitHubClient(system: ActorSystem, private val materializer: ActorMaterializer) {

  private data class GitHubRequest(
    private val eTagOpt: Optional<String>,
    val delayOpt: Optional<Long>) {

    fun toHttpRequest(): HttpRequest {
      val headers = mutableListOf<HttpHeader>().apply {
        eTagOpt.map { tag ->
          add(HttpHeader.parse("If-None-Match", tag))
        }
      }
      return HttpRequest
        .create("https://api.github.com/orgs/kotlin/events")
        .withHeaders(headers)
    }
  }

  private data class GitHubResponse(
    val eTagOpt: Optional<String>,
    val pollIntervalOpt: Optional<Long>,
    val nodesOpt: Optional<ArrayNode>
  )

  private val client = Http.get(system)

  private var lastPollInterval = 0L

  private fun execute(request: GitHubRequest): CompletionStage<GitHubResponse> =
    Source
      .single(request)
      .delay(FiniteDuration(request.delayOpt.orElse(lastPollInterval), TimeUnit.SECONDS), DelayOverflowStrategy.backpressure())
      .mapAsync(1, {
        client.singleRequest(request.toHttpRequest())
          .thenCompose { response -> toGitHubResponse(response) }
      })
      .runWith(Sink.head(), materializer)

  private fun toGitHubResponse(response: HttpResponse): CompletionStage<GitHubResponse> {
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

  fun events(): Source<JsonNode, NotUsed> =
    Source
      .unfoldAsync(GitHubRequest(Optional.empty(), Optional.empty()), { request ->
        execute(request)
          .thenApply { response ->
            val nextRequest = GitHubRequest(response.eTagOpt, response.pollIntervalOpt)
            Optional.of(Pair.create(nextRequest, response))
          }
      })
      .flatMapConcat { response ->
        response.nodesOpt
          .map { nodes -> Source.from(nodes) }
          .orElse(Source.empty())
      }
}
