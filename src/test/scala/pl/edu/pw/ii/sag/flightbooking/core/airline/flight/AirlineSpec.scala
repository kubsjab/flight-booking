package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike
import pl.edu.pw.ii.sag.flightbooking.core.airline.{Airline, AirlineData}
import pl.edu.pw.ii.sag.flightbooking.core.domain.flight.{Plane, Seat}

import scala.concurrent.duration.FiniteDuration

class AirlineSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with WordSpecLike {

  private def airlineData(): AirlineData = AirlineData("airline-1", "Airline name")

  private def randomFlightData(): FlightData = FlightData(
    UUID.randomUUID().toString,
    Plane("Boeing 787", (1 to 100).map(x => Seat(x.toString))),
    ZonedDateTime.now(),
    ZonedDateTime.now(),
    "Warsaw",
    "London")


  "The Airline " should {

    "create flight" in {
      val airline = testKit.spawn(Airline(airlineData()))
      val probe = testKit.createTestProbe[Airline.OperationResult]
      val flightData = randomFlightData()

      airline ! Airline.CreateFlight(flightData, FlightBookingStrategyType.STANDARD, probe.ref)

      probe.expectMessage(Airline.FlightCreationConfirmed(flightData.flightId))
    }

    "reject already created flight" in {
      val airline = testKit.spawn(Airline(airlineData()))
      val probe = testKit.createTestProbe[Airline.OperationResult]
      val flightData = randomFlightData()

      airline ! Airline.CreateFlight(flightData, FlightBookingStrategyType.STANDARD, probe.ref)
      probe.expectMessage(Airline.FlightCreationConfirmed(flightData.flightId))
      airline ! Airline.CreateFlight(flightData, FlightBookingStrategyType.STANDARD, probe.ref)
      probe.expectMessage(Airline.Rejected(s"Flight - [${flightData.flightId}] already exists"))
    }

    "return all existing flights" in {
      val airline = testKit.spawn(Airline(airlineData()))
      val createFlightProbe = testKit.createTestProbe[Airline.OperationResult]
      val flightData = randomFlightData()
      airline ! Airline.CreateFlight(flightData, FlightBookingStrategyType.STANDARD, createFlightProbe.ref)

      val getFlightsProbe = testKit.createTestProbe[Airline.FlightDetailsCollection]
      airline ! Airline.GetFlights(getFlightsProbe.ref)
      val response = getFlightsProbe.receiveMessage(FiniteDuration(5, TimeUnit.SECONDS))
      response.airlines.size should !==(0)
    }


  }

}