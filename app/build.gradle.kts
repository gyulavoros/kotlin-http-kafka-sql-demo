import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  application
  kotlin("jvm") version "1.2.20"
}

application {
  mainClassName = "co.makery.kotlin.Main"
}

dependencies {
  compile(kotlin("stdlib-jdk8", "1.2.20"))
  compile(kotlin("reflect", "1.2.20"))
  compile("ch.qos.logback:logback-classic:1.2.3")
  compile("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.9.3") {
    exclude(group = "org.jetbrains.kotlin")
  }
  compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.3") {
    exclude(group = "org.jetbrains.kotlin")
  }
  compile("com.lightbend.akka:akka-stream-alpakka-slick_2.12:0.16")
  compile("com.typesafe.akka:akka-http_2.12:10.1.0-RC1")
  compile("com.typesafe.akka:akka-http-jackson_2.12:10.1.0-RC1")
  compile("com.typesafe.akka:akka-slf4j_2.12:2.5.9")
  compile("com.typesafe.akka:akka-stream_2.12:2.5.9")
  compile("com.typesafe.akka:akka-stream-kafka_2.12:0.18")
  compile("org.postgresql:postgresql:42.2.0")
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}
