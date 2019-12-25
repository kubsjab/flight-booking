package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import java.time.ZonedDateTime

import pl.edu.pw.ii.sag.flightbooking.core.domain.booking.BookingRequest
import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer

object Booking {

  def apply(booking: BookingRequest): Booking = new Booking(booking.customer, ZonedDateTime.now())

}

case class Booking(customer: Customer, createdDate: ZonedDateTime)
