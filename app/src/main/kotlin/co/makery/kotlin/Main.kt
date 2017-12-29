@file:JvmName("Main")

package co.makery.kotlin

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

fun main(vararg args: String) {
  val system = ActorSystem.create()
  val materializer = ActorMaterializer.create(system)

  val gitHubClient = GitHubClient(system, materializer)
  val eventsProducer = EventsProducer(system, materializer)
  val eventsConsumer = EventsConsumer(system)
  val pushEventProcessor = PushEventProcessor(materializer)

  eventsProducer.run(gitHubClient.events())
  pushEventProcessor.run(eventsConsumer.events())
}
