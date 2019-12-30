package pl.edu.pw.ii.sag.flightbooking.core.configuration

import com.typesafe.config.ConfigFactory

object Configuration {
  val configuration = ConfigFactory.load("application.conf")
  val airlinesCount = configuration.getInt("akka.configuration.simulation.airline.count")
  val brokersCount = configuration.getInt("akka.configuration.simulation.broker.count")
  val clientsCount = configuration.getInt("akka.configuration.simulation.client.count")

  val initialMinFlightCount = configuration.getInt("akka.configuration.simulation.flight.initial.minCount")
  val initialMaxFlightCount = configuration.getInt("akka.configuration.simulation.flight.initial.maxCount")

  val minAirlinesInBrokerCount: Int = Math.max(airlinesCount - 2, 1)
  val maxAirlinesInBrokerCount: Int = airlinesCount
  val minBrokersInClientCount: Int = Math.max(brokersCount - 5, 2)
  val maxBrokersInClientCount: Int = brokersCount
}