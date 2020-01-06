package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.client.ClientManager
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration

object OverbookingSimulationGuardian extends Simulation {
  override val simulationType: SimulationType = SimulationType.OVERBOOKING
  private val initialAgentCount = InitialAgentCount(
    Configuration.Simulation.Overbooking.airlinesCount,
    Configuration.Simulation.Overbooking.brokersCount,
    Configuration.Simulation.Overbooking.clientsCount
  )

  def apply(): Behavior[Simulation.Message] = new OverbookingSimulationGuardian(initialAgentCount).apply()

}
class OverbookingSimulationGuardian(initialAgentCount: InitialAgentCount) extends AbstractSimulationGuardian(initialAgentCount) {

  private val config = Configuration.Simulation.Overbooking

  override def generateAirlines(context: ActorContext[Simulation.Message],
                                airlineGenerator: ActorRef[AirlineGenerator.Command],
                                airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult]): Unit = {
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(config.airlinesCount, airlineGeneratorResponseWrapper)
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

  override def generateFlights(context: ActorContext[Simulation.Message],
                               flightGenerator: ActorRef[FlightGenerator.Command],
                               airlineIds: Set[String]): Unit = {
    flightGenerator ! FlightGenerator.GenerateStandardFlights(airlineIds, config.standardFlight.initialMinCount, config.standardFlight.initialMaxCount)
    flightGenerator ! FlightGenerator.GenerateOverbookingFlights(airlineIds, config.overbookingFlight.initialMinCount, config.overbookingFlight.initialMaxCount)

    if (config.standardFlight.schedulerEnabled) {
      flightGenerator ! FlightGenerator.InitScheduledStandardFlightsGeneration(
        FlightGenerator.GenerateStandardFlights(airlineIds, config.standardFlight.schedulerMinCount, config.standardFlight.schedulerMaxCount),
        FiniteDuration(config.standardFlight.schedulerDelay, duration.SECONDS)
      )
    }
    if (config.overbookingFlight.schedulerEnabled) {
      flightGenerator ! FlightGenerator.InitScheduledOverbookingFlightsGeneration(
        FlightGenerator.GenerateOverbookingFlights(airlineIds, config.overbookingFlight.schedulerMinCount, config.overbookingFlight.schedulerMaxCount),
        FiniteDuration(config.overbookingFlight.schedulerDelay, duration.SECONDS)
      )
    }
  }

  override def initializeClientRequestScheduler(clientManager: ActorRef[ClientManager.Command]): Unit = {
    clientManager ! ClientManager.InitClientsReservationScheduler(
      Configuration.Simulation.Overbooking.clientTicketReservationSchedulerMinDelay,
      Configuration.Simulation.Overbooking.clientTicketReservationSchedulerMaxDelay)
  }
}
