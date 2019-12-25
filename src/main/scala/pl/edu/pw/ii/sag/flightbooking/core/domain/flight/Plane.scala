package pl.edu.pw.ii.sag.flightbooking.core.domain.flight

case class Plane(name: String, seats: Seq[Seat])

case class Seat(id: String)
