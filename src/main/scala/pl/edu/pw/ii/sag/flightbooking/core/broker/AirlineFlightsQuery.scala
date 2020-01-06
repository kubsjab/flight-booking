package pl.edu.pw.ii.sag.flightbooking.core.broker

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.FlightDetails
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable
import pl.edu.pw.ii.sag.flightbooking.util.Aggregator

import scala.concurrent.duration._

object AirlineFlightsQuery {

  sealed trait Command extends CborSerializable

  final case class AggregatedAirlineFlights(airlineFlights: Map[String, Seq[FlightDetails]], replyTo: ActorRef[Broker.AirlineFlightDetailsCollection]) extends Command

  def getFlights(airlines: Seq[ActorRef[Airline.Command]],
                 brokerId: String,
                 replyToWhenCompleted: ActorRef[Broker.AirlineFlightDetailsCollection]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.spawnAnonymous(
        Aggregator[Airline.FlightDetailsCollection, AggregatedAirlineFlights](
          sendRequests = { replyTo =>
            airlines.foreach(airline => airline ! Airline.GetFlights(replyTo))
          },
          expectedReplies = airlines.size,
          replyTo = context.self,
          aggregateReplies = airlineFlights => AggregatedAirlineFlights(
            airlineFlights
              .map(c => c.flights)
              .filter(_.nonEmpty)
              .map(flights => flights.filter(details => !details.isFull))
              .filter(_.nonEmpty)
              .map(flights => flights.head.flightInfo.airlineId -> flights)
              .toMap,
            replyToWhenCompleted),
          timeout = FiniteDuration(Configuration.Core.Broker.flightQueryTimeout, SECONDS)))

      messageHandler(brokerId)
    }

  def getFlightsBySource(source: String,
                         airlines: Seq[ActorRef[Airline.Command]],
                         brokerId: String,
                         replyToWhenCompleted: ActorRef[Broker.AirlineFlightDetailsCollection]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.spawnAnonymous(
        Aggregator[Airline.FlightDetailsCollection, AggregatedAirlineFlights](
          sendRequests = { replyTo =>
            airlines.foreach(airline => airline ! Airline.GetFlightsBySource(source, replyTo))
          },
          expectedReplies = airlines.size,
          replyTo = context.self,
          aggregateReplies = airlineFlights => AggregatedAirlineFlights(
            airlineFlights
              .map(c => c.flights)
              .filter(_.nonEmpty)
              .map(flights => flights.filter(details => !details.isFull))
              .filter(_.nonEmpty)
              .map(flights => flights.head.flightInfo.airlineId -> flights)
              .toMap,
            replyToWhenCompleted),
          timeout = FiniteDuration(Configuration.Core.Broker.flightQueryTimeout, SECONDS)))

      messageHandler(brokerId)
    }

  def getFlightsBySourceAndDestination(source: String,
                                       destination: String,
                                       airlines: Seq[ActorRef[Airline.Command]],
                                       brokerId: String,
                                       replyToWhenCompleted: ActorRef[Broker.AirlineFlightDetailsCollection]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.spawnAnonymous(
        Aggregator[Airline.FlightDetailsCollection, AggregatedAirlineFlights](
          sendRequests = { replyTo =>
            airlines.foreach(airline => airline ! Airline.GetFlightsBySourceAndDestination(source, destination, replyTo))
          },
          expectedReplies = airlines.size,
          replyTo = context.self,
          aggregateReplies = airlineFlights => AggregatedAirlineFlights(
            airlineFlights
              .map(c => c.flights)
              .filter(_.nonEmpty)
              .map(flights => flights.filter(details => !details.isFull))
              .filter(_.nonEmpty)
              .map(flights => flights.head.flightInfo.airlineId -> flights)
              .toMap,
            replyToWhenCompleted),
          timeout = FiniteDuration(Configuration.Core.Broker.flightQueryTimeout, SECONDS)))

      messageHandler(brokerId)
    }

  private def messageHandler(brokerId: String): Behavior[Command] = {
    Behaviors.receiveMessage {
      case AggregatedAirlineFlights(airlineFlights, replyTo) =>
        replyTo ! Broker.AirlineFlightDetailsCollection(airlineFlights, brokerId)
        Behaviors.stopped
    }
  }

}
