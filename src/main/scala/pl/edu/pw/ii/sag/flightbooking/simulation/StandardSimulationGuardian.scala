package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.{AirlineGenerator, BrokerGenerator, ClientGenerator}

object StandardSimulationGuardian extends Simulation {

  override val name: String = "basic"

  def apply(): Behavior[Simulation.Start] = {
    Behaviors.setup[Simulation.Start] { context =>
      context.log.info("Starting standard simulation")

      val airlineGenerator = context.spawn(AirlineGenerator(), "airline-generator")
      airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(3)

      val brokerGenerator = context.spawn(BrokerGenerator(), "broker-generator")
      brokerGenerator ! BrokerGenerator.GenerateStandardBrokers(10)

      val clientGenerator = context.spawn(ClientGenerator(), "client-generator")
      clientGenerator ! ClientGenerator.GenerateStandardClients(100)

      Behaviors.empty
    }
  }

}
