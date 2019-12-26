package pl.edu.pw.ii.sag.flightbooking.simulation.generation.data

import pl.edu.pw.ii.sag.flightbooking.core.domain.flight.{Plane, Seat}

object PlaneGenerator {

  def getRandomPlane(): Plane = { // TODO Provide simple dictionary
    Plane("Boeing 787", (1 to 100).map(x => Seat(x.toString)))
  }

}
