package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.AirlineManager
import pl.edu.pw.ii.sag.flightbooking.core.broker.BrokerManager
import pl.edu.pw.ii.sag.flightbooking.core.client.ClientManager
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

final case class InitialAgentCount(
                                    airline: Int,
                                    broker: Int,
                                    client: Int
                                  )

abstract class AbstractSimulationGuardian(val initialAgentCount: InitialAgentCount) {

  final case class AirlinesGenerated(response: AirlineGenerator.OperationResult) extends Simulation.Message

  final case class FlightsGenerated(response: AirlineGenerator.OperationResult) extends Simulation.Message

  final case class BrokersGenerated(response: BrokerGenerator.OperationResult) extends Simulation.Message

  final case class ClientsGenerated(response: ClientGenerator.OperationResult) extends Simulation.Message


  /**
   * Behaviour of this Actor defines initial simulation setup. In general whole simulation consists of 3 main actor layers:
   * - airlines
   * - brokers
   * - clients
   *
   * Each airline offers some flight options, which can be booked by broker on client's request. Although it is safe to create
   * all airlines, brokers and clients independently, flights and booking requests should be created once all core actors
   * are properly configured and settled up. Otherwise race condition can occur during simulation setup.
   *
   */
  def apply(): Behavior[Simulation.Message] = Behaviors.setup[Simulation.Message] { context =>
    val airlineManager = context.spawn(AirlineManager(), AirlineManager.TAG)
    val airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult] = context.messageAdapter(rsp => AirlinesGenerated(rsp))
    val brokerManager = context.spawn(BrokerManager(), BrokerManager.TAG)
    val brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult] = context.messageAdapter(rsp => BrokersGenerated(rsp))
    val clientManager = context.spawn(ClientManager(), ClientManager.TAG)
    val clientGeneratorResponseWrapper: ActorRef[ClientGenerator.OperationResult] = context.messageAdapter(rsp => ClientsGenerated(rsp))

    val airlineGenerator = context.spawn(AirlineGenerator(airlineManager), "airline-generator")
    val flightGenerator = context.spawn(FlightGenerator(airlineManager), "flight-generator")
    val brokerGenerator = context.spawn(BrokerGenerator(brokerManager, airlineManager), "broker-generator")
    val clientGenerator = context.spawn(ClientGenerator(clientManager, brokerManager), "client-generator")


    Behaviors.receiveMessage[Simulation.Message] {
      case Simulation.Start() =>
        simulationStarted(context, airlineGenerator, airlineGeneratorResponseWrapper)
      case AirlinesGenerated(response: AirlineGenerator.OperationResult) =>
        airlinesGenerated(context, flightGenerator, brokerGenerator, response, brokerGeneratorResponseWrapper)
      case BrokersGenerated(response: BrokerGenerator.OperationResult) =>
        brokersGenerated(context, clientGenerator, response, clientGeneratorResponseWrapper)
      case ClientsGenerated(response: ClientGenerator.OperationResult) =>
        clientsGenerated(context, clientManager, response)
      case _ => Behaviors.same
    }

  }

  private def simulationStarted(context: ActorContext[Simulation.Message],
                                airlineGenerator: ActorRef[AirlineGenerator.Command],
                                airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult]): Behavior[Simulation.Message] = {
    generateAirlines(context, airlineGenerator, airlineGeneratorResponseWrapper)
    Behaviors.same
  }

  private def airlinesGenerated(context: ActorContext[Simulation.Message],
                                flightGenerator: ActorRef[FlightGenerator.Command],
                                brokerGenerator: ActorRef[BrokerGenerator.Command],
                                response: AirlineGenerator.OperationResult,
                                brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult]): Behavior[Simulation.Message] = {
    response match {
      case AirlineGenerator.AirlineGenerationCompleted(airlineIds) if airlineIds.size == initialAgentCount.airline =>
        generateFlights(context, flightGenerator, airlineIds)
        generateBrokers(context, brokerGenerator, airlineIds, brokerGeneratorResponseWrapper)
        Behaviors.same
      case AirlineGenerator.AirlineGenerationCompleted(_) =>
        context.log.error("Airlines count does not match expected airlines count")
        Behaviors.stopped
      case AirlineGenerator.Rejected(reason) =>
        context.log.error("Failed to generate airlines. " + reason)
        Behaviors.stopped
    }
  }

  private def brokersGenerated(context: ActorContext[Simulation.Message],
                               clientGenerator: ActorRef[ClientGenerator.Command],
                               response: BrokerGenerator.OperationResult,
                               clientGeneratorResponseWrapper: ActorRef[ClientGenerator.OperationResult]): Behavior[Simulation.Message] = {
    response match {
      case BrokerGenerator.BrokerGenerationCompleted(brokerIds) if brokerIds.size == initialAgentCount.broker =>
        generateClients(context, clientGenerator, brokerIds, clientGeneratorResponseWrapper)
        Behaviors.same
      case BrokerGenerator.BrokerGenerationCompleted(_) =>
        context.log.error("Brokers count does not match expected brokers count")
        Behaviors.stopped
      case BrokerGenerator.Rejected(reason) =>
        context.log.error("Failed to generate brokers. " + reason)
        Behaviors.stopped
    }
  }

  private def clientsGenerated(context: ActorContext[Simulation.Message],
                               clientManager: ActorRef[ClientManager.Command],
                               response: ClientGenerator.OperationResult): Behavior[Simulation.Message] = {
    response match {
      case ClientGenerator.ClientGenerationCompleted(clientIds) if clientIds.size == initialAgentCount.client =>
        context.log.info("Initiating client schedulers for ticket reservation")
        initializeClientRequestScheduler(clientManager)
        context.log.info("Initiating client schedulers for reservation cancelling")
        initializeClientCancelRequestScheduler(clientManager)
        Behaviors.same
      case ClientGenerator.ClientGenerationCompleted(_) =>
        context.log.error("Clients count does not match expected clients count")
        Behaviors.stopped
      case ClientGenerator.Rejected(reason) =>
        context.log.error("Failed to generate clients. " + reason)
        Behaviors.stopped
    }
  }

  protected def generateAirlines(context: ActorContext[Simulation.Message],
                                 airlineGenerator: ActorRef[AirlineGenerator.Command],
                                 airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult]): Unit

  protected def generateFlights(context: ActorContext[Simulation.Message],
                                flightGenerator: ActorRef[FlightGenerator.Command],
                                airlineIds: Set[String]): Unit

  protected def generateBrokers(context: ActorContext[Simulation.Message],
                                brokerGenerator: ActorRef[BrokerGenerator.Command],
                                airlineIds: Set[String],
                                brokerGeneratorResponseWrapper: ActorRef[BrokerGenerator.OperationResult]): Unit

  protected def generateClients(context: ActorContext[Simulation.Message],
                                clientGenerator: ActorRef[ClientGenerator.Command],
                                brokersIds: Set[String],
                                clientGeneratorResponseWrapper: ActorRef[ClientGenerator.OperationResult]): Unit

  protected def initializeClientRequestScheduler(clientManager: ActorRef[ClientManager.Command]): Unit

  protected def initializeClientCancelRequestScheduler(clientManager: ActorRef[ClientManager.Command]): Unit
}
