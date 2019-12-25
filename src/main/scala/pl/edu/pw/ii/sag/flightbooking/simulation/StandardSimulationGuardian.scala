package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

object StandardSimulationGuardian extends Simulation {

  override val simulationType: SimulationType = SimulationType.STANDARD

  def apply(): Behavior[Simulation.Start] = {
    Behaviors.setup[Simulation.Start] { context =>
      context.log.info("Starting standard simulation")

      generateAirlines(context)
      generateFlights(context)
      generateBrokers(context)
      generateClients(context)

      Behaviors.empty
    }
  }

  private def generateAirlines(context: ActorContext[Simulation.Start]): Unit = {
    val airlineGenerator = context.spawn(AirlineGenerator(), "airline-generator")
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(3)
  }

  private def generateFlights(context: ActorContext[Simulation.Start]): Unit = {
    val flightGenerator = context.spawn(FlightGenerator(), "flight-generator")
    flightGenerator ! FlightGenerator.GenerateStandardFlights(20, 30)
  }

  private def generateBrokers(context: ActorContext[Simulation.Start]): Unit = {
    val brokerGenerator = context.spawn(BrokerGenerator(), "broker-generator")
    brokerGenerator ! BrokerGenerator.GenerateStandardBrokers(10)
  }

  private def generateClients(context: ActorContext[Simulation.Start]): Unit = {
    val clientGenerator = context.spawn(ClientGenerator(), "client-generator")
    clientGenerator ! ClientGenerator.GenerateStandardClients(100)
  }

}
