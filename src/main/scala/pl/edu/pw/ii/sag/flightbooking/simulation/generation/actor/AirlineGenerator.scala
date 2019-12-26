package pl.edu.pw.ii.sag.flightbooking.simulation.generation.actor

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.{Airline, AirlineData, AirlineManager}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.util.Aggregator

import scala.concurrent.duration._


object AirlineGenerator {

  // command
  sealed trait Command extends CborSerializable
  final case class GenerateStandardAirlines(count: Int, replyTo: ActorRef[OperationResult]) extends Command
  final case class AggregatedAirlines(airlineIds: Set[String], replyTo: ActorRef[OperationResult]) extends Command

  // reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  final case class AirlineGenerationCompleted(airlineIds: Set[String]) extends OperationResult
  final case class Rejected(reason: String) extends OperationResult


  def apply(airlineManager: ActorRef[AirlineManager.Command]): Behavior[Command] = Behaviors.receive { (context, message: Command) =>
    message match {
      case GenerateStandardAirlines(count, replyTo) => generateAirlines(context, airlineManager, count, replyTo)
      case AggregatedAirlines(airlineIds, replyTo) => confirmAirlineGenerationCompletion(airlineIds, replyTo)
      case _ => Behaviors.same
    }
  }

  private def generateAirlines(context: ActorContext[Command],
                               airlineManager: ActorRef[AirlineManager.Command],
                               count: Int,
                               replyToWhenCompleted: ActorRef[OperationResult]): Behavior[Command] = {
    context.log.info("Generating {} airlines", count)
    context.spawnAnonymous(
      Aggregator[AirlineManager.OperationResult, AggregatedAirlines](
        sendRequests = { replyTo =>
          (1 to count).foreach(i => {
            airlineManager ! AirlineManager.CreateAirline(AirlineData(Airline.buildId(i.toString), s"Airline-$i"), replyTo)
          })
        },
        expectedReplies = count,
        context.self,
        aggregateReplies = replies =>
          AggregatedAirlines(
            replies
              .filter(_.isInstanceOf[AirlineManager.AirlineCreationConfirmed])
              .map(x => x.asInstanceOf[AirlineManager.AirlineCreationConfirmed].airlineId)
              .toSet,
            replyToWhenCompleted),
        timeout = 5.seconds))
    Behaviors.same
  }

  private def confirmAirlineGenerationCompletion(airlineIds: Set[String], replyTo: ActorRef[OperationResult]): Behavior[Command] ={
    replyTo ! AirlineGenerator.AirlineGenerationCompleted(airlineIds)
    Behaviors.same
  }

}
