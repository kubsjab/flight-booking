package pl.edu.pw.ii.sag.flightbooking.core.broker

import java.time.ZonedDateTime

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

object FlightBooking {

  sealed trait Command extends CborSerializable
  private final case class WrappedBookingOperationResult(response: Flight.BookingOperationResult) extends Command

  def apply(airline: ActorRef[Airline.Command],
            flightId: String,
            seatId: String,
            customer: Customer,
            requestedDate: ZonedDateTime,
            replyTo: ActorRef[Broker.BookingOperationResult]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      val airlineGeneratorResponseWrapper: ActorRef[Flight.BookingOperationResult] = context.messageAdapter(rsp => WrappedBookingOperationResult(rsp))

      airline ! Airline.BookFlight(flightId, seatId, customer, requestedDate, airlineGeneratorResponseWrapper)

      Behaviors.receiveMessage {
        case WrappedBookingOperationResult(rsp) => handleBookingOperationResult(rsp, replyTo)
        case _ => Behaviors.same
      }
    }

  private def handleBookingOperationResult(response: Flight.BookingOperationResult, replyTo: ActorRef[Broker.BookingOperationResult]): Behavior[Command] = {
    response match {
      case Flight.BookingAccepted(bookingId) => replyTo ! Broker.BookingAccepted(bookingId)
      case Flight.BookingRejected(reason) => replyTo ! Broker.BookingRejected(reason)
    }
    Behaviors.stopped
  }

}
