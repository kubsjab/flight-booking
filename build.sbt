name := "flight-booking"

version := "0.1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.2",
  "org.postgresql" % "postgresql" % "42.2.9",

  "com.google.guava" % "guava" % "28.1-jre",

  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)
