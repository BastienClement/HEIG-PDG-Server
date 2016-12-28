package models

import utils.Coordinates
import utils.SlickAPI._

case class PointOfInterest(id: Int, event: Int, title: String, desc: String, lat: Double, lon: Double) {
	def location: Coordinates = (lat, lon)
}

//noinspection TypeAnnotation
class PointsOfInterest(tag: Tag) extends Table[PointOfInterest](tag, "pois") {
	def id = column[Int]("id", O.PrimaryKey)
	def event = column[Int]("event_id")
	def title = column[String]("title")
	def desc = column[String]("description")
	def lat = column[Double]("lat")
	def lon = column[Double]("lon")

	def * = (id, event, title, desc, lat, lon) <> (PointOfInterest.tupled, PointOfInterest.unapply)
}

object PointsOfInterest extends TableQuery(new PointsOfInterest(_)) {

}
