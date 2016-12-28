package models

import play.api.libs.json._
import utils.SlickAPI._
import utils.{Coordinates, UsingImplicits}

case class PointOfInterest(id: Int, event: Int, title: String, desc: String, lat: Double, lon: Double)
		extends UsingImplicits[PointsOfInterest] {
	def location: Coordinates = (lat, lon)
}

//noinspection TypeAnnotation
class PointsOfInterest(tag: Tag) extends Table[PointOfInterest](tag, "pois") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def event = column[Int]("event_id")
	def title = column[String]("title")
	def desc = column[String]("description")
	def lat = column[Double]("lat")
	def lon = column[Double]("lon")

	def * = (id, event, title, desc, lat, lon) <> (PointOfInterest.tupled, PointOfInterest.unapply)
}

object PointsOfInterest extends TableQuery(new PointsOfInterest(_)) {
	def findById(id: Int) = PointsOfInterest.filter(poi => poi.id === id)
	def findByEvent(event: Int) = PointsOfInterest.filter(poi => poi.event === event)
	def findByKey(event: Int, id: Int) = PointsOfInterest.filter(poi => poi.event === event && poi.id === id)

	implicit val PointOfInterestFormat = new Format[PointOfInterest] {
		def writes(poi: PointOfInterest): JsValue = Json.obj(
			"id" -> poi.id,
			"event" -> poi.event,
			"title" -> poi.title,
			"desc" -> poi.desc,
			"location" -> poi.location
		)
		def reads(json: JsValue): JsResult[PointOfInterest] = for {
			id <- (json \ "id").validateOpt[Int].map(_.getOrElse(0))
			event <- (json \ "event").validate[Int]
			title <- (json \ "title").validate[String]
			desc <- (json \ "desc").validateOpt[String].map(_.getOrElse(""))
			location <- (json \ "location").validate[JsArray]
			lat <- location(0).validate[Double]
			lon <- location(1).validate[Double]
		} yield PointOfInterest(id, event, title, desc, lat, lon)
	}
}
