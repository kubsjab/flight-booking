package pl.edu.pw.ii.sag.flightbooking.simulation.generation.data

import java.time.ZonedDateTime
import java.util.UUID

import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightInfo

object FlightDataGenerator {

  // TODO Provide some simple utility or dictionary to match source and destination and trip time
  //  so that duration of trips between same airports will at least similar
  def generateRandomFlightInfo(): FlightInfo = {
    val startDateTime = ZonedDateTime.now()
    val endDateTime = startDateTime.plusHours(3)
    FlightInfo(UUID.randomUUID().toString, "", PlaneGenerator.getRandomPlane(), startDateTime, endDateTime, "Warsaw", "London")
  }

}
