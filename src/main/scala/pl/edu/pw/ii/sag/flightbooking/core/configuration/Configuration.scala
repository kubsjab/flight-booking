package pl.edu.pw.ii.sag.flightbooking.core.configuration

import com.typesafe.config.ConfigFactory

object Configuration {
  val configuration = ConfigFactory.load("application.conf")

  object Core {

    object Broker {
      val bookingTimeout = configuration.getInt("configuration.core.broker.booking-timeout")
      val cancelBookingTimeout = configuration.getInt("configuration.core.broker.cancel-booking-timeout")
      val flightQueryTimeout = configuration.getInt("configuration.core.broker.flight-query-timeout")
    }

    object Airline {
      val flightQueryTimeout = configuration.getInt("configuration.core.airline.flight-query-timeout")
    }

    object Client {
      val bookingTimeout = configuration.getInt("configuration.core.client.booking-timeout")
      val flightQueryTimeout = configuration.getInt("configuration.core.client.flight-query-timeout")
      val cancelBookingTimeout: Int = configuration.getInt("configuration.core.broker.cancel-booking-timeout")
    }

  }

  object Simulation {

    object DataGenerator {
      val flightRoutesCount: Int = configuration.getInt("configuration.simulation.generation.routes")
      val cityFileName: String = configuration.getString("configuration.simulation.generation.citySourceFile")
      val planeFileName: String = configuration.getString("configuration.simulation.generation.planeSourceFile")
    }

    object Standard {
      val airlinesCount: Int = configuration.getInt("configuration.simulation.standard.airline.count")
      val brokersCount: Int = configuration.getInt("configuration.simulation.standard.broker.count")
      val clientsCount: Int = configuration.getInt("configuration.simulation.standard.client.count")

      val clientTicketReservationSchedulerMinDelay: Int = configuration.getInt("configuration.simulation.standard.client.scheduler.ticket-reservation.minDelay")
      val clientTicketReservationSchedulerMaxDelay: Int = configuration.getInt("configuration.simulation.standard.client.scheduler.ticket-reservation.maxDelay")
      val clientCancelReservationSchedulerMinDelay: Int = configuration.getInt("configuration.simulation.standard.client.scheduler.reservation-cancelling.minDelay")
      val clientCancelReservationSchedulerMaxDelay: Int = configuration.getInt("configuration.simulation.standard.client.scheduler.reservation-cancelling.maxDelay")

      val flight: Flight = Flight(
        initialMinCount = configuration.getInt("configuration.simulation.standard.flight.initial.minCount"),
        initialMaxCount = configuration.getInt("configuration.simulation.standard.flight.initial.maxCount"),
        schedulerEnabled = configuration.getBoolean("configuration.simulation.standard.flight.scheduler.enabled"),
        schedulerDelay = configuration.getInt("configuration.simulation.standard.flight.scheduler.delay"),
        schedulerMinCount = configuration.getInt("configuration.simulation.standard.flight.scheduler.minCount"),
        schedulerMaxCount = configuration.getInt("configuration.simulation.standard.flight.scheduler.maxCount")
      )


      val minAirlinesInBrokerCount: Int = 1
      val maxAirlinesInBrokerCount: Int = Math.max(airlinesCount / 2, 1)
      val minBrokersInClientCount: Int = 1
      val maxBrokersInClientCount: Int = Math.max(brokersCount / 5, 1)

    }

    object Delayed {
      val airlinesCount: Int = configuration.getInt("configuration.simulation.delayed.airline.count")
      val brokersCount: Int = configuration.getInt("configuration.simulation.delayed.broker.count")
      val clientsCount: Int = configuration.getInt("configuration.simulation.delayed.client.count")

      val clientTicketReservationSchedulerMinDelay: Int = configuration.getInt("configuration.simulation.delayed.client.scheduler.ticket-reservation.minDelay")
      val clientTicketReservationSchedulerMaxDelay: Int = configuration.getInt("configuration.simulation.delayed.client.scheduler.ticket-reservation.maxDelay")
      val clientCancelReservationSchedulerMinDelay: Int = configuration.getInt("configuration.simulation.delayed.client.scheduler.reservation-cancelling.minDelay")
      val clientCancelReservationSchedulerMaxDelay: Int = configuration.getInt("configuration.simulation.delayed.client.scheduler.reservation-cancelling.maxDelay")

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

      val minAirlinesInBrokerCount: Int = 1
      val maxAirlinesInBrokerCount: Int = Math.max(airlinesCount / 2, 1)
      val minBrokersInClientCount: Int = 1
      val maxBrokersInClientCount: Int = Math.max(brokersCount / 5, 1)

    }

    object Overbooking {
      val airlinesCount: Int = configuration.getInt("configuration.simulation.overbooking.airline.count")
      val brokersCount: Int = configuration.getInt("configuration.simulation.overbooking.broker.count")
      val clientsCount: Int = configuration.getInt("configuration.simulation.overbooking.client.count")

      val clientTicketReservationSchedulerMinDelay: Int = configuration.getInt("configuration.simulation.overbooking.client.scheduler.ticket-reservation.minDelay")
      val clientTicketReservationSchedulerMaxDelay: Int = configuration.getInt("configuration.simulation.overbooking.client.scheduler.ticket-reservation.maxDelay")
      val clientCancelReservationSchedulerMinDelay: Int = configuration.getInt("configuration.simulation.overbooking.client.scheduler.reservation-cancelling.minDelay")
      val clientCancelReservationSchedulerMaxDelay: Int = configuration.getInt("configuration.simulation.overbooking.client.scheduler.reservation-cancelling.maxDelay")

      val standardFlight: Flight = Flight(
        initialMinCount = configuration.getInt("configuration.simulation.overbooking.flight.standard.initial.minCount"),
        initialMaxCount = configuration.getInt("configuration.simulation.overbooking.flight.standard.initial.maxCount"),
        schedulerEnabled = configuration.getBoolean("configuration.simulation.overbooking.flight.standard.scheduler.enabled"),
        schedulerDelay = configuration.getInt("configuration.simulation.overbooking.flight.standard.scheduler.delay"),
        schedulerMinCount = configuration.getInt("configuration.simulation.overbooking.flight.standard.scheduler.minCount"),
        schedulerMaxCount = configuration.getInt("configuration.simulation.overbooking.flight.standard.scheduler.maxCount")
      )

      val overbookingFlight: Flight = Flight(
        initialMinCount = configuration.getInt("configuration.simulation.overbooking.flight.overbooking.initial.minCount"),
        initialMaxCount = configuration.getInt("configuration.simulation.overbooking.flight.overbooking.initial.maxCount"),
        schedulerEnabled = configuration.getBoolean("configuration.simulation.overbooking.flight.overbooking.scheduler.enabled"),
        schedulerDelay = configuration.getInt("configuration.simulation.overbooking.flight.overbooking.scheduler.delay"),
        schedulerMinCount = configuration.getInt("configuration.simulation.overbooking.flight.overbooking.scheduler.minCount"),
        schedulerMaxCount = configuration.getInt("configuration.simulation.overbooking.flight.overbooking.scheduler.maxCount")
      )

      val minAirlinesInBrokerCount: Int = 1
      val maxAirlinesInBrokerCount: Int = Math.max(airlinesCount / 2, 1)
      val minBrokersInClientCount: Int = 1
      val maxBrokersInClientCount: Int = Math.max(brokersCount / 5, 1)

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