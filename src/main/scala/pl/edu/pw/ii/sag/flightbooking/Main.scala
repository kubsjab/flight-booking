package pl.edu.pw.ii.sag.flightbooking

import akka.actor.typed.{ActorSystem, Behavior}
import pl.edu.pw.ii.sag.flightbooking.simulation.{BasicSimulationGuardian, OverbookingSimulationGuardian, Simulation}

object Main {

  def main(args: Array[String]): Unit = {
    val simulationGuardian: Behavior[Simulation.Start] = args.headOption match {
      case Some(BasicSimulationGuardian.name) => BasicSimulationGuardian()
      case Some(OverbookingSimulationGuardian.name) => OverbookingSimulationGuardian()
      case None =>
        throw new IllegalArgumentException("Simulation type name must be provided")
    }
    val system: ActorSystem[Simulation.Start] = ActorSystem(simulationGuardian, "flight-booking")
    system ! Simulation.Start()

  }

}
