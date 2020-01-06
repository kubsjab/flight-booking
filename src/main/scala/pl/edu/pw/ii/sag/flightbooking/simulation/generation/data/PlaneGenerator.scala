package pl.edu.pw.ii.sag.flightbooking.simulation.generation.data

import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.core.domain.flight.{Plane, Seat}

import scala.io.Source
import scala.util.Random

object PlaneGenerator {

  val planeDataSet: Seq[Plane] = readData()


  def getRandomPlane(): Plane = {
    planeDataSet(Random.nextInt(planeDataSet.length))
  }

  private def readData(): Seq[Plane] = {
    Source.fromResource(Configuration.Simulation.DataGenerator.planeFileName).getLines()
      .map(line => {
        val Array(name, seats) = line.split(",")
        Plane(name, (1 to seats.toInt).map(x => Seat(x.toString)))
      })
      .toSeq
  }

}
