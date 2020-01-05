package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import akka.persistence.typed.scaladsl.ReplyEffect
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight._
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyBehaviourProvider

object StandardFlightBookingStrategy {

  def apply(replyBehaviourProvider: ReplyBehaviourProvider): StandardFlightBookingStrategy = new StandardFlightBookingStrategy(replyBehaviourProvider)

}

class StandardFlightBookingStrategy(behaviourProvider: ReplyBehaviourProvider) extends FlightBookingStrategy(behaviourProvider) {

  override protected def bookFlight(flightState: OpenedFlight, cmd: Book): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      return behaviourProvider.reply(cmd.replyTo, BookingRejected("Invalid flightId", cmd.requestId))
    }
    if (flightState.isBooked(cmd.seatId)) {
      behaviourProvider.reply(cmd.replyTo, BookingRejected(s"Seat with id ${cmd.seatId} is already booked", cmd.requestId))
    }
    else {
      val booking = Booking.createBooking(cmd.customer)
      behaviourProvider.persistAndReply(Booked(cmd.seatId, booking), cmd.replyTo, BookingAccepted(booking.bookingId, cmd.requestId))
    }
  }

  override protected def cancelBooking(flightState: OpenedFlight, cmd: CancelBooking): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      return behaviourProvider.reply(cmd.replyTo, CancelBookingRejected("Invalid flightId"))
    }
    val bookedSeatId = flightState.getSeatByBookingId(cmd.bookingId)
    if (bookedSeatId.isEmpty) {
      behaviourProvider.reply(cmd.replyTo, CancelBookingRejected(s"Booking with id ${cmd.bookingId} does not exist"))
    }
    else {
      behaviourProvider.persistAndReply(BookingCancelled(bookedSeatId.get), cmd.replyTo, CancelBookingAccepted())
    }
  }

  override protected def closeFlight(flightState: OpenedFlight, cmd: CloseFlight): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      behaviourProvider.reply(cmd.replyTo, CloseFlightRejected("Invalid flightId"))
    }
    else {
      behaviourProvider.persistAndReply(FlightClosed(), cmd.replyTo, CloseFlightAccepted())
    }
  }

}
