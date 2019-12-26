package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import pl.edu.pw.ii.sag.flightbooking.core.airline.AirlineManager
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

object StandardSimulationGuardian extends Simulation {

  override val simulationType: SimulationType = SimulationType.STANDARD

  def apply(): Behavior[Simulation.Start] = {
    Behaviors.setup[Simulation.Start] { context =>
      context.log.info("Starting standard simulation")

      generateAirlinesAndFlights(context)
      generateBrokers(context)
      generateClients(context)

      Behaviors.empty
    }
  }

  private def generateAirlinesAndFlights(context: ActorContext[Simulation.Start]): Unit = {
    val airlineManager = context.spawn(AirlineManager(), "airline-manager")
    generateAirlines(context, airlineManager)
    generateFlights(context, airlineManager, Seq.empty)
  }

  private def generateAirlines(context: ActorContext[Simulation.Start], airlineManager: ActorRef[AirlineManager.Command]): Unit = {
    val airlineGenerator = context.spawn(AirlineGenerator(airlineManager), "airline-generator")
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(3)
  }

  private def generateFlights(context: ActorContext[Simulation.Start],
                              airlineManager: ActorRef[AirlineManager.Command],
                              airlineIds: Seq[String]): Unit = {
    val flightGenerator = context.spawn(FlightGenerator(airlineManager), "flight-generator")
    flightGenerator ! FlightGenerator.GenerateStandardFlights(airlineIds, 20, 30)
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
