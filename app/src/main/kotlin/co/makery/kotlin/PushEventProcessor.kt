package co.makery.kotlin

import akka.Done
import akka.NotUsed
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.javadsl.Slick
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.javadsl.Source
import com.fasterxml.jackson.databind.JsonNode
import java.sql.Timestamp
import java.util.concurrent.CompletionStage

class PushEventProcessor(private val materializer: ActorMaterializer) {

  companion object {
    private val session = SlickSession.forConfig("slick-postgres")
  }

  private fun createTableIfNotExists(): Source<Int, NotUsed> {
    val ddl =
      """
        |CREATE TABLE IF NOT EXISTS kotlin_push_events(
        |  id         BIGINT    NOT NULL,
        |  name       VARCHAR   NOT NULL,
        |  timestamp  TIMESTAMP NOT NULL,
        |  repository VARCHAR   NOT NULL,
        |  branch     VARCHAR   NOT NULL,
        |  commits    INTEGER   NOT NULL
        |);
        |CREATE UNIQUE INDEX IF NOT EXISTS id_index ON kotlin_push_events (id);
      """.trimMargin()
    return Slick.source(session, ddl, { _ -> 0 })
  }

  private fun Source<JsonNode, NotUsed>.filterPushEvents(): Source<PushEvent, NotUsed> =
    filter { node -> node["type"].asText() == "PushEvent" }
      .map { node -> objectMapper.convertValue(node, PushEvent::class.java) }

  private fun Source<PushEvent, NotUsed>.updateDatabase(): CompletionStage<Done> =
    createTableIfNotExists().flatMapConcat { this }
      .runWith(Slick.sink<PushEvent>(session, 20, { event ->
        """
          |INSERT INTO kotlin_push_events(id, name, timestamp, repository, branch, commits)
          |VALUES (
          |  ${event.id},
          |  '${event.actor.login}',
          |  '${Timestamp.valueOf(event.created_at)}',
          |  '${event.repo.name}',
          |  '${event.payload.ref}',
          |  ${event.payload.distinct_size}
          |)
          |ON CONFLICT DO NOTHING
        """.trimMargin()
      }), materializer)

  fun run(events: Source<JsonNode, NotUsed>): CompletionStage<Done> =
    events
      .filterPushEvents()
      .updateDatabase()
}
