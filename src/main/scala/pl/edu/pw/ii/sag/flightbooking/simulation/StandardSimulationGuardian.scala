package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.{AirlineGenerator, BrokerGenerator, ClientGenerator}

object StandardSimulationGuardian extends Simulation {

  override val name: String = "standard"

  def apply(): Behavior[Simulation.Start] = {
    Behaviors.setup[Simulation.Start] { context =>
      context.log.info("Starting standard simulation")

      generateAirlines(context)

      val brokerGenerator = context.spawn(BrokerGenerator(), "broker-generator")
      brokerGenerator ! BrokerGenerator.GenerateStandardBrokers(10)

      val clientGenerator = context.spawn(ClientGenerator(), "client-generator")
      clientGenerator ! ClientGenerator.GenerateStandardClients(100)

      Behaviors.empty
    }
  }

  private def generateAirlines(context: ActorContext[Simulation.Start]): Unit ={
    val airlineGenerator = context.spawn(AirlineGenerator(), "airline-generator")
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(3)
  }

}
