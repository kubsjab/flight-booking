package pl.edu.pw.ii.sag.flightbooking.core.airline.query

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.{Flight, FlightDetails}
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.util.Aggregator

import scala.concurrent.duration._

object FlightQuery {

  sealed trait Command extends CborSerializable

  final case class AggregatedFlights(flights: Seq[FlightDetails], replyTo: ActorRef[Airline.FlightDetailsCollection]) extends Command

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
          aggregateReplies = flightDetailsMsgSeq => AggregatedFlights(flightDetailsMsgSeq.map(fdm => fdm.flightDetails), replyToWhenCompleted),
          timeout = FiniteDuration(Configuration.Core.Airline.flightQueryTimeout, SECONDS)))

      Behaviors.receiveMessage {
        case AggregatedFlights(flights, replyTo) =>
          replyTo ! Airline.FlightDetailsCollection(flights)
          Behaviors.stopped
      }
    }

}
