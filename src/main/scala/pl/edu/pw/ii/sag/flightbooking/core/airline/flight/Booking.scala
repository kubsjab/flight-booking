package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import java.time.ZonedDateTime
import java.util.UUID

import pl.edu.pw.ii.sag.flightbooking.core.domain.booking.BookingRequest
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer

object Booking {

  def createBooking(booking: BookingRequest): Booking = new Booking(UUID.randomUUID().toString, booking.customer, ZonedDateTime.now())

}

case class Booking(bookingId: String, customer: Customer, createdDate: ZonedDateTime)
