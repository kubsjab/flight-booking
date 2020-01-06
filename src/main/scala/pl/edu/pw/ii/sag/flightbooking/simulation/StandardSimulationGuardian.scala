package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.client.ClientManager
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration

object StandardSimulationGuardian extends Simulation {
  override val simulationType: SimulationType = SimulationType.STANDARD
  private val initialAgentCount = InitialAgentCount(
    Configuration.Simulation.Standard.airlinesCount,
    Configuration.Simulation.Standard.brokersCount,
    Configuration.Simulation.Standard.clientsCount
  )

  def apply(): Behavior[Simulation.Message] = new StandardSimulationGuardian(initialAgentCount).apply()
}

class StandardSimulationGuardian(initialAgentCount: InitialAgentCount) extends AbstractSimulationGuardian(initialAgentCount) {

  private val config = Configuration.Simulation.Standard

  override def generateAirlines(context: ActorContext[Simulation.Message],
                                airlineGenerator: ActorRef[AirlineGenerator.Command],
                                airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult]): Unit = {
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(config.airlinesCount, airlineGeneratorResponseWrapper)
  }

  override def generateFlights(context: ActorContext[Simulation.Message],
                               flightGenerator: ActorRef[FlightGenerator.Command],
                               airlineIds: Set[String]): Unit = {
    flightGenerator ! FlightGenerator.GenerateStandardFlights(airlineIds, config.flight.initialMinCount, config.flight.initialMaxCount)
    if (config.flight.schedulerEnabled) {
      flightGenerator ! FlightGenerator.InitScheduledStandardFlightsGeneration(
        FlightGenerator.GenerateStandardFlights(airlineIds, config.flight.schedulerMinCount, config.flight.schedulerMaxCount),
        FiniteDuration(config.flight.schedulerDelay, duration.SECONDS)
      )
    }
  }

  override def generateBrokers(context: ActorContext[Simulation.Message],
                               brokerGenerator: ActorRef[BrokerGenerator.Command],
                               airlineIds: Set[String],
                               brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult]): Unit = {
    brokerGenerator ! BrokerGenerator.GenerateStandardBrokers(
      config.brokersCount, airlineIds, config.minAirlinesInBrokerCount, config.maxAirlinesInBrokerCount, brokerGeneratorResponseWrapper)
  }

  override def generateClients(context: ActorContext[Simulation.Message],
                               clientGenerator: ActorRef[ClientGenerator.Command],
                               brokersIds: Set[String],
                               clientGeneratorResponseWrapper: ActorRef[ClientGenerator.OperationResult]): Unit = {
    clientGenerator ! ClientGenerator.GenerateStandardClients(
      config.clientsCount, brokersIds, config.minBrokersInClientCount, config.maxBrokersInClientCount, clientGeneratorResponseWrapper)
  }

  override def initializeClientRequestScheduler(clientManager: ActorRef[ClientManager.Command]): Unit = {
    clientManager ! ClientManager.InitClientsReservationScheduler(
      Configuration.Simulation.Standard.clientTicketReservationSchedulerMinDelay,
      Configuration.Simulation.Standard.clientTicketReservationSchedulerMaxDelay)
  }
}
