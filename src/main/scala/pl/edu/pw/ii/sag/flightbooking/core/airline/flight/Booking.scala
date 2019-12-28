package pl.edu.pw.ii.sag.flightbooking.core.airline.flight

import java.time.ZonedDateTime
import java.util.UUID

import pl.edu.pw.ii.sag.flightbooking.core.domain.customer.Customer

object Booking {

  def createBooking(customer: Customer): Booking = new Booking(UUID.randomUUID().toString, customer, ZonedDateTime.now())

}

case class Booking(bookingId: String, customer: Customer, createdDate: ZonedDateTime)
