package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object BasicSimulationGuardian extends Simulation {

  override val name: String = "basic"

  def apply(): Behavior[Simulation.Start] = {
    Behaviors.setup[Simulation.Start] { context =>
      context.log.info("Starting basic simulation")
      Behaviors.empty
    }
  }

}
