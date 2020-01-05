package pl.edu.pw.ii.sag.flightbooking.core.configuration

import com.typesafe.config.ConfigFactory

object Configuration {
  val configuration = ConfigFactory.load("application.conf")

  object Core {

    object Broker {
      val bookingTimeout = configuration.getInt("configuration.core.broker.booking-timeout")
      val cancelBookingTimeout = configuration.getInt("configuration.core.broker.cancel-booking-timeout")
    }

  }

  val airlinesCount = configuration.getInt("configuration.simulation.airline.count")
  val brokersCount = configuration.getInt("configuration.simulation.broker.count")
  val clientsCount = configuration.getInt("configuration.simulation.client.count")

  val initialMinFlightCount = configuration.getInt("configuration.simulation.flight.initial.minCount")
  val initialMaxFlightCount = configuration.getInt("configuration.simulation.flight.initial.maxCount")
  val flightSchedulerEnabled = configuration.getBoolean("configuration.simulation.flight.scheduler.enabled")
  val flightSchedulerDelay = configuration.getInt("configuration.simulation.flight.scheduler.delay")
  val schedulerMinFlightCount = configuration.getInt("configuration.simulation.flight.scheduler.minCount")
  val schedulerMaxFlightCount = configuration.getInt("configuration.simulation.flight.scheduler.maxCount")

  val minAirlinesInBrokerCount: Int = Math.max(airlinesCount - 2, 1)
  val maxAirlinesInBrokerCount: Int = airlinesCount
  val minBrokersInClientCount: Int = Math.max(brokersCount - 5, 2)
  val maxBrokersInClientCount: Int = brokersCount

  val flightRoutesCount: Int = configuration.getInt("configuration.simulation.flight.generation.routes")
  val cityFileName: String = configuration.getString("configuration.simulation.flight.generation.citySourceFile")
  val planeFileName: String = configuration.getString("configuration.simulation.flight.generation.planeSourceFile")

  val minFlightResponseDelay: Int = 1
  val maxFlightResponseDelay: Int = 5
}