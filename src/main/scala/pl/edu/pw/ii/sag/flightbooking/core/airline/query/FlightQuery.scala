package pl.edu.pw.ii.sag.flightbooking.core.airline.query

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.{Flight, FlightDetailsWrapper}
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.util.Aggregator

import scala.concurrent.duration._

object FlightQuery {

  sealed trait Command extends CborSerializable

  final case class AggregatedFlights(airlines: Seq[FlightDetailsWrapper], replyTo: ActorRef[Airline.FlightDetailsCollection]) extends Command

  def apply(
             flightActors: Seq[ActorRef[Flight.Command]],
             replyToWhenCompleted: ActorRef[Airline.FlightDetailsCollection]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.spawnAnonymous(
        Aggregator[Flight.FlightDetailsMessage, AggregatedFlights](
          sendRequests = { replyTo =>
            flightActors.foreach(flightActor => flightActor ! Flight.GetFlightDetails(replyTo))
          },
          expectedReplies = flightActors.size,
          replyTo = context.self,
          aggregateReplies = flightDetailsMsgSeq => AggregatedFlights(flightDetailsMsgSeq.map(fdm => fdm.flightDetailsWrapper), replyToWhenCompleted),
          timeout = 5.seconds))

      Behaviors.receiveMessage {
        case AggregatedFlights(airlines, replyTo) =>
          replyTo ! Airline.FlightDetailsCollection(airlines)
          Behaviors.stopped
      }
    }

}
