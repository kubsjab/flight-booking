package pl.edu.pw.ii.sag.flightbooking.eventsourcing

import com.google.common.base.CaseFormat

class TaggingAdapter[Event]() {

  def tag(event: Event): Set[String] = {
    val eventTag = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, event.getClass.getSimpleName)
    Set(eventTag)
  }

}
