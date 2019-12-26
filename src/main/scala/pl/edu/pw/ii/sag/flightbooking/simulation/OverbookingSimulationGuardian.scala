package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType

object OverbookingSimulationGuardian extends Simulation {

  override val simulationType: SimulationType = SimulationType.OVERBOOKING

  def apply(): Behavior[Simulation.Message] = {
    Behaviors.setup[Simulation.Message] { context =>
      context.log.info("Starting overbooking simulation")
      Behaviors.empty
    }
  }

}
