package co.makery.kotlin

import java.time.LocalDateTime

data class Repository(
  val id: Long,
  val name: String,
  val url: String
)

data class Actor(
  val id: Long,
  val login: String
)

data class PushEventPayload(
  val distinct_size: Int,
  val ref: String
)

data class PushEvent(
  val actor: Actor,
  val created_at: LocalDateTime,
  val id: Long,
  val payload: PushEventPayload,
  val repo: Repository
)
