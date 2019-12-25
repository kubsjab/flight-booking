package pl.edu.pw.ii.sag.flightbooking.simulation

import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType

object SimulationType extends Enumeration {
  type SimulationType = Value
  val STANDARD, OVERBOOKING = Value

  def of(s: String): Option[Value] = values.find(_.toString.equalsIgnoreCase(s))
}

object Simulation {

  final case class Start()

}

trait Simulation {

  val simulationType: SimulationType

}
