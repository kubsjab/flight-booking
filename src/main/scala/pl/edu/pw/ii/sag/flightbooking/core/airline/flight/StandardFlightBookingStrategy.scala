package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.ReplyEffect
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.Flight._
import pl.edu.pw.ii.sag.flightbooking.core.airline.flight.replyStrategy.ReplyBehaviourProvider

object StandardFlightBookingStrategy {

  def apply(replyBehaviourProvider: ReplyBehaviourProvider): StandardFlightBookingStrategy = new StandardFlightBookingStrategy(replyBehaviourProvider)

}

class StandardFlightBookingStrategy(behaviourProvider: ReplyBehaviourProvider) extends FlightBookingStrategy(behaviourProvider) {

  override protected def bookFlight(context: ActorContext[Flight.Command], flightState: OpenedFlight, cmd: Book): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      return behaviourProvider.reply(cmd.replyTo, BookingRejected("Invalid flightId", cmd.requestId))
    }
    if (flightState.isBooked(cmd.seatId)) {
      context.log.info(s"Unable to book seat: [${cmd.seatId}] as it is already booked.")
      behaviourProvider.reply(cmd.replyTo, BookingRejected(s"Seat with id ${cmd.seatId} is already booked", cmd.requestId))
    }
    else {
      val booking = Booking.createBooking(cmd.customer)
      context.log.info(s"Saving booking: [${booking.bookingId}] for seat: [${cmd.seatId}]")
      behaviourProvider.persistAndReply(Booked(cmd.seatId, booking), cmd.replyTo, BookingAccepted(booking.bookingId, cmd.requestId))
    }
  }

  override protected def cancelBooking(context: ActorContext[Flight.Command], flightState: OpenedFlight, cmd: CancelBooking): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      context.log.warn(s"Provided flightId is invalid: [${cmd.flightId}]")
      return behaviourProvider.reply(cmd.replyTo, CancelBookingRejected("Invalid flightId", cmd.requestId))
    }
    val bookedEntry = flightState.getSeatEntryByBookingId(cmd.bookingId)
    if (bookedEntry.isEmpty) {
      context.log.warn(s"Failed to cancel booking with id ${cmd.bookingId} as it does not exist")
      behaviourProvider.reply(cmd.replyTo, CancelBookingRejected(s"Booking with id ${cmd.bookingId} does not exist", cmd.requestId))
    }
    else {
      context.log.info(s"Cancelling booking with id ${cmd.bookingId}")
      behaviourProvider.persistAndReply(BookingCancelled(bookedEntry.get._1, bookedEntry.get._2), cmd.replyTo, CancelBookingAccepted(cmd.requestId))
    }
  }

  override protected def closeFlight(context: ActorContext[Flight.Command], flightState: OpenedFlight, cmd: CloseFlight): ReplyEffect[Event, State] = {
    if (!flightState.isFlightIdValid(cmd.flightId)) {
      context.log.warn(s"Provided flightId is invalid: [${cmd.flightId}]")
      behaviourProvider.reply(cmd.replyTo, CloseFlightRejected("Invalid flightId"))
    }
    else {
      context.log.info(s"Closing flight with flightId: [${cmd.flightId}]")
      behaviourProvider.persistAndReply(FlightClosed(), cmd.replyTo, CloseFlightAccepted())
    }
  }

}
