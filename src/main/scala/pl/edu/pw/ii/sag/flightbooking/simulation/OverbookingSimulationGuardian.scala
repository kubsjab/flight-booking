package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object OverbookingSimulationGuardian extends Simulation {

  override val name: String = "overbooking"

  def apply(): Behavior[Simulation.Start] = {
    Behaviors.setup[Simulation.Start] { context =>
      context.log.info("Starting overbooking simulation")
      Behaviors.empty
    }
  }

}
