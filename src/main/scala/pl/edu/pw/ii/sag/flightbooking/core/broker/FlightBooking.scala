package pl.edu.pw.ii.sag.flightbooking.core.broker

import java.time.ZonedDateTime
import java.util.concurrent.TimeoutException

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import pl.edu.pw.ii.sag.flightbooking.core.airline.Airline
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight
import pl.edu.pw.ii.sag.flightbooking.core.configuration.Configuration
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer
import pl.edu.pw.ii.sag.flightbooking.serialization.CborSerializable

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object FlightBooking {

  sealed trait Command extends CborSerializable
  private final case class FailedResult(exception: Throwable) extends Command
  private final case class WrappedBookingOperationResult(response: Flight.BookingOperationResult) extends Command
  private final case class WrappedCancelBookingOperationResult(response: Flight.CancelBookingOperationResult) extends Command

  def bookFlight(airline: ActorRef[Airline.Command],
                 flightId: String,
                 seatId: String,
                 customer: Customer,
                 requestedDate: ZonedDateTime,
                 replyTo: ActorRef[Broker.BookingOperationResult]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      implicit val timeout: Timeout = FiniteDuration(Configuration.Core.Broker.bookingTimeout, SECONDS)

      context.ask(airline, (ref: ActorRef[Flight.BookingOperationResult]) => Airline.BookFlight(flightId, seatId, customer, requestedDate, ref)) {
        case Success(rsp) => WrappedBookingOperationResult(rsp)
        case Failure(ex) => FailedResult(ex)
      }

      Behaviors.receiveMessage {
        case WrappedBookingOperationResult(rsp) => handleBookingOperationResult(rsp, replyTo)
        case FailedResult(ex) => handleBookingFailedResult(ex, replyTo)
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

  private def handleBookingFailedResult(exception: Throwable, replyTo: ActorRef[Broker.BookingOperationResult]): Behavior[Command] = {
    exception match {
      case _: TimeoutException => replyTo ! Broker.Timeout()
      case _ => replyTo ! Broker.BookingRejected(exception.getMessage)
    }
    Behaviors.stopped
  }

  def cancelFlightBooking(airline: ActorRef[Airline.Command],
                          flightId: String,
                          bookingId: String,
                          replyTo: ActorRef[Broker.CancelBookingOperationResult]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      implicit val timeout: Timeout = FiniteDuration(Configuration.Core.Broker.cancelBookingTimeout, SECONDS)

      context.ask(airline, (ref: ActorRef[Flight.CancelBookingOperationResult]) => Airline.CancelFlightBooking(flightId, bookingId, ref)) {
        case Success(rsp) => WrappedCancelBookingOperationResult(rsp)
        case Failure(ex) => FailedResult(ex)
      }

      Behaviors.receiveMessage {
        case WrappedCancelBookingOperationResult(rsp) => handleCancelBookingOperationResult(rsp, replyTo)
        case FailedResult(ex) => handleCancelBookingFailedResult(ex, replyTo)
        case _ => Behaviors.same
      }
    }

  private def handleCancelBookingOperationResult(response: Flight.CancelBookingOperationResult, replyTo: ActorRef[Broker.CancelBookingOperationResult]): Behavior[Command] = {
    response match {
      case Flight.CancelBookingAccepted() => replyTo ! Broker.CancelBookingAccepted()
      case Flight.CancelBookingRejected(reason) => replyTo ! Broker.CancelBookingRejected(reason)
    }
    Behaviors.stopped
  }

  private def handleCancelBookingFailedResult(exception: Throwable, replyTo: ActorRef[Broker.CancelBookingOperationResult]): Behavior[Command] = {
    exception match {
      case _: TimeoutException => replyTo ! Broker.Timeout()
      case _ => replyTo ! Broker.CancelBookingRejected(exception.getMessage)
    }
    Behaviors.stopped
  }

}
