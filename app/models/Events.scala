package models

import play.api.libs.json.{Format, JsResult, JsValue, Json}
import slick.jdbc.GetResult
import slick.lifted.TableQuery
import utils.SlickAPI._
import utils.{Coordinates, DateTime, UsingImplicits}

case class Event(id: Int, title: String, owner: Int, desc: String, begin: DateTime, end: DateTime, spontaneous: Boolean,
                 lat: Double, lon: Double, radius: Double) extends UsingImplicits[Events] {
	require(radius >= 0 && radius <= 500, "Event radius must be 0-500")
	require(begin <= end, "Event dates must verify 'begin <= end'")

	def location = Coordinates(lat, lon)
}

//noinspection TypeAnnotation
class Events(tag: Tag) extends Table[Event](tag, "events") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def owner = column[Int]("owner_id")
	def desc = column[String]("description")
	def begin = column[DateTime]("begin_time")
	def end = column[DateTime]("end_time")
	def spontaneous = column[Boolean]("spontaneous")
	def lat = column[Double]("lat")
	def lon = column[Double]("lon")
	def radius = column[Double]("radius")

	def * = (id, title, owner, desc, begin, end, spontaneous, lat, lon, radius) <> (Event.tupled, Event.unapply)
}

object Events extends TableQuery(new Events(_)) {
	/** Raw SQL Event reader */
	implicit val UserGetResult = GetResult(r => Event(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

	implicit val EventFormat: Format[Event] = new Format[Event] {
		def writes(event: Event): JsValue = Json.obj(
			"id" -> event.id,
			"title" -> event.title,
			"owner" -> event.owner,
			"desc" -> event.desc,
			"begin" -> event.begin,
			"end" -> event.end,
			"spontaneous" -> event.spontaneous,
			"location" -> event.location,
			"radius" -> event.radius
		)
		def reads(json: JsValue): JsResult[Event] = for {
			id <- (json \ "id").validate[Int]
			title <- (json \ "title").validate[String]
			owner <- (json \ "owner").validate[Int]
			desc <- (json \ "desc").validate[String]
			begin <- (json \ "begin").validate[DateTime]
			end <- (json \ "end").validate[DateTime]
			spontaneous <- (json \ "spontaneous").validate[Boolean]
			location <- (json \ "location").validate[Coordinates]
			radius <- (json \ "radius").validate[Double]
		} yield Event(id, title, owner, desc, begin, end, spontaneous, location.lat, location.lon, radius)
	}

	val findById = Events.findBy(_.id)
}
