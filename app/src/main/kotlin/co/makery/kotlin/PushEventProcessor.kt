package co.makery.kotlin

import akka.Done
import akka.NotUsed
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.javadsl.Slick
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.javadsl.Source
import com.fasterxml.jackson.databind.JsonNode
import java.sql.Timestamp
import java.util.concurrent.CompletableFuture


class PushEventProcessor(private val materializer: ActorMaterializer) {

  companion object {
    private val session = SlickSession.forConfig("slick-postgres")
  }

  private fun createTable(): Source<Int, NotUsed> {
    val ddl =
      """
        |CREATE TABLE IF NOT EXISTS kotlin_push_events(
        |  name       VARCHAR   NOT NULL,
        |  timestamp  TIMESTAMP NOT NULL,
        |  repository VARCHAR   NOT NULL,
        |  branch     VARCHAR   NOT NULL,
        |  commits    INTEGER   NOT NULL
        |);
      """.trimMargin()
    return Slick.source(session, ddl, { _ -> 0 })
  }

  private fun transform(source: Source<JsonNode, NotUsed>): Source<PushEvent, NotUsed> =
    source
      .filter { node -> node["type"].asText() == "PushEvent" }
      .map { node -> objectMapper.convertValue(node, PushEvent::class.java) }

  private fun insert(source: Source<PushEvent, NotUsed>) =
    createTable()
      .flatMapConcat { _ -> source }
      .runWith(Slick.sink<PushEvent>(session, { event ->
        """
          |INSERT INTO kotlin_push_events(name, timestamp, repository, branch, commits)
          |VALUES (
          |  '${event.actor.login}',
          |  '${Timestamp.valueOf(event.created_at)}',
          |  '${event.repo.name}',
          |  '${event.payload.ref}',
          |  ${event.payload.distinct_size}
          |)
        """.trimMargin()
      }), materializer)

  fun run(source: Source<JsonNode, NotUsed>): CompletableFuture<Done> =
    insert(transform(source)).toCompletableFuture()
}
