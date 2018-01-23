package co.makery.kotlin

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.javadsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Source
import com.fasterxml.jackson.databind.JsonNode
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.concurrent.CompletionStage

class EventsProducer(system: ActorSystem, private val materializer: ActorMaterializer) {

  private val settings: ProducerSettings<ByteArray, String> = ProducerSettings
    .create(system, ByteArraySerializer(), StringSerializer())
    .withBootstrapServers("localhost:9092")

  fun write(events: Source<JsonNode, NotUsed>): CompletionStage<Done> =
    events
      .map { node -> ProducerRecord<ByteArray, String>("kotlin-events", objectMapper.writeValueAsString(node)) }
      .runWith(Producer.plainSink(settings), materializer)
}
