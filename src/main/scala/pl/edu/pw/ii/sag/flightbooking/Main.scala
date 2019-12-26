package pl.edu.pw.ii.sag.flightbooking

import akka.actor.typed.{ActorSystem, Behavior}
import pl.edu.pw.ii.sag.flightbooking.simulation.{OverbookingSimulationGuardian, Simulation, SimulationType, StandardSimulationGuardian}

object Main {

  def main(args: Array[String]): Unit = {
    val simulationGuardian: Behavior[Simulation.Message] = args.headOption.flatMap(augmentString => SimulationType.of(augmentString)) match {
      case Some(StandardSimulationGuardian.simulationType) => StandardSimulationGuardian()
      case Some(OverbookingSimulationGuardian.simulationType) => OverbookingSimulationGuardian()
      case None =>
        throw new IllegalArgumentException("Simulation type name must be provided")
    }
    val system: ActorSystem[Simulation.Start] = ActorSystem(simulationGuardian, "flight-booking")
    system ! Simulation.Start()
  }

}
