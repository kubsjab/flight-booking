package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight._

object StandardFlightBookingStrategy {

  def apply(): StandardFlightBookingStrategy = new StandardFlightBookingStrategy()

}

class StandardFlightBookingStrategy extends FlightBookingStrategy {

  override protected def bookFlight(flightState: OpenedFlight, cmd: Book): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      return Effect.reply(cmd.replyTo)(BookingRejected("Invalid flightId", cmd.requestId))
    }
    if (flightState.isBooked(cmd.seatId)) {
      Effect.reply(cmd.replyTo)(BookingRejected(s"Seat with id ${cmd.seatId} is already booked", cmd.requestId))
    }
    else {
      val booking = Booking.createBooking(cmd.customer)
      Effect
        .persist(Booked(cmd.seatId, booking))
        .thenReply(cmd.replyTo)(_ => BookingAccepted(booking.bookingId, cmd.requestId))
    }
  }

  override protected def cancelBooking(flightState: OpenedFlight, cmd: CancelBooking): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      return Effect.reply(cmd.replyTo)(CancelBookingRejected("Invalid flightId"))
    }
    val bookedEntry = flightState.getSeatEntryByBookingId(cmd.bookingId)
    if (bookedEntry.isEmpty) {
      Effect.reply(cmd.replyTo)(CancelBookingRejected(s"Booking with id ${cmd.bookingId} does not exist"))
    }
    else {
      Effect
        .persist(BookingCancelled(bookedEntry.get._1, bookedEntry.get._2))
        .thenReply(cmd.replyTo)(_ => CancelBookingAccepted())
    }
  }

  override protected def closeFlight(flightState: OpenedFlight, cmd: CloseFlight): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      Effect.reply(cmd.replyTo)(CloseFlightRejected("Invalid flightId"))
    }
    else {
      Effect
        .persist(FlightClosed())
        .thenReply(cmd.replyTo)(_ => CloseFlightAccepted())
    }
  }

}
