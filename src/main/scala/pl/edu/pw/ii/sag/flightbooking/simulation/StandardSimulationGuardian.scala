package pl.edu.pw.ii.sag.flightbooking.simulation

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.AirlineManager
import pl.edu.pw.ii.sag.flightbooking.simulation.SimulationType.SimulationType
import pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor.{AirlineGenerator, BrokerGenerator, ClientGenerator, FlightGenerator}

object StandardSimulationGuardian extends Simulation {

  override val simulationType: SimulationType = SimulationType.STANDARD

  final case class AirlinesGenerated(response: AirlineGenerator.OperationResult) extends Simulation.Message
  final case class FlightsGenerated(response: AirlineGenerator.OperationResult) extends Simulation.Message
  final case class BrokersGenerated() extends Simulation.Message
  final case class ClientsGenerated() extends Simulation.Message

  // TODO Move params to configuration https://doc.akka.io/docs/akka/current/extending-akka.html#extending-akka-settings
  val airlinesCount = 3
  val minFlightCount = 10
  val maxFlightCount = 20
  val brokersCount = 10
  val clientsCount = 100

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
    val airlineManager = context.spawn(AirlineManager(), "airline-manager")
    val airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult] = context.messageAdapter(rsp => AirlinesGenerated(rsp))

    Behaviors.receiveMessage[Simulation.Message] {
      case Simulation.Start() =>
        simulationStarted(context, airlineManager, airlineGeneratorResponseWrapper)
      case AirlinesGenerated(response: AirlineGenerator.OperationResult) =>
        airlinesGenerated(context, airlineManager, response)
      case _ => Behaviors.same
    }

  }

  private def simulationStarted(context: ActorContext[Simulation.Message],
                                airlineManager: ActorRef[AirlineManager.Command],
                                airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult]): Behavior[Simulation.Message] = {
    generateAirlines(context, airlineManager, airlineGeneratorResponseWrapper, airlinesCount)
    Behaviors.same
  }

  private def generateAirlines(context: ActorContext[Simulation.Message],
                               airlineManager: ActorRef[AirlineManager.Command],
                               airlineGeneratorResponseWrapper: ActorRef[AirlineGenerator.OperationResult],
                               airlinesCount: Int): Unit = {
    val airlineGenerator = context.spawn(AirlineGenerator(airlineManager), "airline-generator")
    airlineGenerator ! AirlineGenerator.GenerateStandardAirlines(airlinesCount, airlineGeneratorResponseWrapper)
  }

  private def airlinesGenerated(context: ActorContext[Simulation.Message],
                                airlineManager: ActorRef[AirlineManager.Command],
                                response: AirlineGenerator.OperationResult): Behavior[Simulation.Message] = {
    response match {
      case AirlineGenerator.AirlineGenerationCompleted(airlineIds) if airlineIds.size == airlinesCount =>
        generateFlights(context, airlineManager, airlineIds, minFlightCount, maxFlightCount)
        generateBrokers(context, brokersCount)
        generateClients(context, clientsCount) // TODO Probably when brokers and clients (mainly clients) are created, we should create some initial booking requests (similarly to airlines and flights)
        Behaviors.same
      case AirlineGenerator.AirlineGenerationCompleted(_) =>
        context.log.error("Airlines count does not match expected airlines count")
        Behaviors.stopped
      case AirlineGenerator.Rejected(reason) =>
        context.log.error("Failed to generate airlines. " + reason)
        Behaviors.stopped
    }
  }

  private def generateFlights(context: ActorContext[Simulation.Message],
                              airlineManager: ActorRef[AirlineManager.Command],
                              airlineIds: Set[String],
                              minFlightCount: Int,
                              maxFlightCount: Int): Unit = {
    val flightGenerator = context.spawn(FlightGenerator(airlineManager), "flight-generator")
    flightGenerator ! FlightGenerator.GenerateStandardFlights(airlineIds, minFlightCount, maxFlightCount)
  }

  private def generateBrokers(context: ActorContext[Simulation.Message], brokersCount: Int): Unit = {
    val brokerGenerator = context.spawn(BrokerGenerator(), "broker-generator")
    brokerGenerator ! BrokerGenerator.GenerateStandardBrokers(brokersCount)
  }

  private def generateClients(context: ActorContext[Simulation.Message], clientsCount: Int): Unit = {
    val clientGenerator = context.spawn(ClientGenerator(), "client-generator")
    clientGenerator ! ClientGenerator.GenerateStandardClients(clientsCount)
  }

}
