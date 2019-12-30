package pl.edu.pw.ii.sag.flightbooking.core.configuration

import com.typesafe.config.ConfigFactory

object Configuration {
  val configuration = ConfigFactory.load("application.conf")
  val airlinesCount = configuration.getInt("akka.configuration.airlinesCount")
  val minFlightCount = configuration.getInt("akka.configuration.minFlightCount")
  val maxFlightCount = configuration.getInt("akka.configuration.maxFlightCount")
  val brokersCount = configuration.getInt("akka.configuration.brokersCount")
  val clientsCount = configuration.getInt("akka.configuration.clientsCount")
  val minAirlinesInBrokerCount: Int = Math.max(airlinesCount - 2, 1)
  val maxAirlinesInBrokerCount: Int = airlinesCount
  val minBrokersInClientCount: Int = Math.max(brokersCount - 5, 2)
  val maxBrokersInClientCount: Int = brokersCount
}