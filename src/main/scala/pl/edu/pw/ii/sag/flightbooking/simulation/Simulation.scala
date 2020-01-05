package pl.edu.pw.ii.sag.flightbooking.simulation

import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType

object SimulationType extends Enumeration {
  type SimulationType = Value
  val STANDARD, OVERBOOKING, DELAYED = Value

  def of(s: String): Option[Value] = values.find(_.toString.equalsIgnoreCase(s))
}

object Simulation {

  trait Message extends CborSerializable
  final case class Start() extends Message

}

trait Simulation {

  val simulationType: SimulationType

}
