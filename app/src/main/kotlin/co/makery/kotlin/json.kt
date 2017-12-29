package co.makery.kotlin

import akka.http.javadsl.marshallers.jackson.Jackson
import akka.http.javadsl.model.HttpEntity
import akka.http.javadsl.unmarshalling.Unmarshaller
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

val objectMapper: ObjectMapper = ObjectMapper()
  .registerModule(KotlinModule())
  .registerModule(JavaTimeModule())
  .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)

val arrayUnmarshaller: Unmarshaller<HttpEntity, ArrayNode> =
  Jackson.unmarshaller(objectMapper, ArrayNode::class.java)
