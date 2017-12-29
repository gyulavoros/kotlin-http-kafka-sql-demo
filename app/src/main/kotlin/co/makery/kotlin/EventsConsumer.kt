package co.makery.kotlin

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.javadsl.Consumer
import akka.stream.javadsl.Source
import com.fasterxml.jackson.databind.JsonNode
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer

class EventsConsumer(system: ActorSystem) {

  private val settings: ConsumerSettings<ByteArray, String> =
    ConsumerSettings.create(system, ByteArrayDeserializer(), StringDeserializer())
      .withBootstrapServers("localhost:9092")
      .withGroupId("group")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  fun events(): Source<JsonNode, NotUsed> =
    Consumer.plainSource(settings, Subscriptions.assignmentWithOffset(TopicPartition("kotlin-events", 0), 0L))
      .map { record ->
        objectMapper.readTree(record.value())
      }
      .mapMaterializedValue { NotUsed.getInstance() }
}
