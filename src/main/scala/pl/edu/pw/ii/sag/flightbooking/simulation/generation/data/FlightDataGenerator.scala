package pl.edu.pw.ii.sag.flightbooking.simulation.generation.data

import java.time.ZonedDateTime
import java.util.UUID

import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightInfo
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration

import scala.collection.mutable
import scala.io.Source
import scala.util.Random

case class Route(from: String, to: String)(val duration: Int)

object FlightDataGenerator {

  private val MAX_FLIGHT_TIME_IN_MINUTES = 2000
  private val MAX_FLIGHT_TIME_DIFFERENCE = 30
  private val ROUTES_NUMBER = Configuration.Simulation.DataGenerator.flightRoutesCount
  val routes: Seq[Route] = generateRoutes(ROUTES_NUMBER)

  def generateRandomFlightInfo(): FlightInfo = {
    val route = routes(Random.nextInt(ROUTES_NUMBER))
    val randomTimeDiff = Random.nextInt(MAX_FLIGHT_TIME_DIFFERENCE)
    val startDateTime = ZonedDateTime.now()
    val endDateTime = startDateTime.plusMinutes(route.duration + randomTimeDiff)
    FlightInfo(
      UUID.randomUUID().toString,
      "",
      PlaneGenerator.getRandomPlane(),
      startDateTime,
      endDateTime,
      route.from,
      route.to
    )
  }

  private def readData(): Seq[String] = {
    Source.fromResource(Configuration.Simulation.DataGenerator.cityFileName).getLines()
      .toSeq
  }

  private def generateRoutes(number: Int): Seq[Route] = {
    val cityDataSet = readData()

    if (ROUTES_NUMBER > cityDataSet.length * (cityDataSet.length - 1))
      throw new IllegalArgumentException(s"Cannot generate $ROUTES_NUMBER different routes from given city list")

    val routes: mutable.Set[Route] = mutable.Set.empty
    while (routes.size < number) {
      val src = getRandomCity(cityDataSet)
      val dst = getRandomCity(cityDataSet)
      if (src != dst)
        routes += Route(src, dst)(Random.nextInt(MAX_FLIGHT_TIME_IN_MINUTES))
    }
    routes.toSeq
  }

  private def getRandomCity(cityDataSet: Seq[String]): String = {
    cityDataSet(Random.nextInt(cityDataSet.length))
  }

}