package pl.edu.pw.ii.sag.flightbooking.core.configuration

import com.typesafe.config.ConfigFactory
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationConfig

object Configuration {
  val configuration = ConfigFactory.load("application.conf")

  object Core {

    object Broker {
      val bookingTimeout: Int = configuration.getInt("configuration.core.broker.booking-timeout")
      val cancelBookingTimeout: Int = configuration.getInt("configuration.core.broker.cancel-booking-timeout")
    }

  }

  object Simulation {

    object DataGenerator {
      val flightRoutesCount: Int = configuration.getInt("configuration.simulation.generation.routes")
      val cityFileName: String = configuration.getString("configuration.simulation.generation.citySourceFile")
      val planeFileName: String = configuration.getString("configuration.simulation.generation.planeSourceFile")
    }

    object Standard extends SimulationConfig {
      val airlinesCount: Int = configuration.getInt("configuration.simulation.standard.airline.count")
      val brokersCount: Int = configuration.getInt("configuration.simulation.standard.broker.count")
      val clientsCount: Int = configuration.getInt("configuration.simulation.standard.client.count")

      val flight: Flight = Flight(
        initialMinCount = configuration.getInt("configuration.simulation.standard.flight.initial.minCount"),
        initialMaxCount = configuration.getInt("configuration.simulation.standard.flight.initial.maxCount"),
        schedulerEnabled = configuration.getBoolean("configuration.simulation.standard.flight.scheduler.enabled"),
        schedulerDelay = configuration.getInt("configuration.simulation.standard.flight.scheduler.delay"),
        schedulerMinCount = configuration.getInt("configuration.simulation.standard.flight.scheduler.minCount"),
        schedulerMaxCount = configuration.getInt("configuration.simulation.standard.flight.scheduler.maxCount")
      )


      val minAirlinesInBrokerCount: Int = Math.max(airlinesCount - 2, 1)
      val maxAirlinesInBrokerCount: Int = airlinesCount
      val minBrokersInClientCount: Int = Math.max(brokersCount - 5, 2)
      val maxBrokersInClientCount: Int = brokersCount

    }

    object Delayed {
      val airlinesCount: Int = configuration.getInt("configuration.simulation.delayed.airline.count")
      val brokersCount: Int = configuration.getInt("configuration.simulation.delayed.broker.count")
      val clientsCount: Int = configuration.getInt("configuration.simulation.delayed.client.count")

      val standardFlight: Flight = Flight(
        initialMinCount = configuration.getInt("configuration.simulation.delayed.flight.standard.initial.minCount"),
        initialMaxCount = configuration.getInt("configuration.simulation.delayed.flight.standard.initial.maxCount"),
        schedulerEnabled = configuration.getBoolean("configuration.simulation.delayed.flight.standard.scheduler.enabled"),
        schedulerDelay = configuration.getInt("configuration.simulation.delayed.flight.standard.scheduler.delay"),
        schedulerMinCount = configuration.getInt("configuration.simulation.delayed.flight.standard.scheduler.minCount"),
        schedulerMaxCount = configuration.getInt("configuration.simulation.delayed.flight.standard.scheduler.maxCount")
      )

      val delayedFlight: Flight = Flight(
        initialMinCount = configuration.getInt("configuration.simulation.delayed.flight.delayed.initial.minCount"),
        initialMaxCount = configuration.getInt("configuration.simulation.delayed.flight.delayed.initial.maxCount"),
        schedulerEnabled = configuration.getBoolean("configuration.simulation.delayed.flight.delayed.scheduler.enabled"),
        schedulerDelay = configuration.getInt("configuration.simulation.delayed.flight.delayed.scheduler.delay"),
        schedulerMinCount = configuration.getInt("configuration.simulation.delayed.flight.delayed.scheduler.minCount"),
        schedulerMaxCount = configuration.getInt("configuration.simulation.delayed.flight.delayed.scheduler.maxCount"),
        minDelay = configuration.getInt("configuration.simulation.delayed.flight.delayed.minDelay"),
        maxDelay = configuration.getInt("configuration.simulation.delayed.flight.delayed.maxDelay")
      )

      val minAirlinesInBrokerCount: Int = Math.max(airlinesCount - 2, 1)
      val maxAirlinesInBrokerCount: Int = airlinesCount
      val minBrokersInClientCount: Int = Math.max(brokersCount - 5, 2)
      val maxBrokersInClientCount: Int = brokersCount

    }

    object Overbooking extends SimulationConfig {

    }

  }

}


final case class Flight(
                         initialMinCount: Int,
                         initialMaxCount: Int,
                         schedulerEnabled: Boolean,
                         schedulerDelay: Int,
                         schedulerMinCount: Int,
                         schedulerMaxCount: Int,
                         minDelay: Int = 0,
                         maxDelay: Int = 0
                       )