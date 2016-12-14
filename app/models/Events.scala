package models

import play.api.libs.json.{Format, Json}
import slick.lifted.TableQuery
import utils.SlickAPI._
import utils.{DateTime, UsingImplicits}

case class Event(id: Int, title: String, owner: Int, desc: String, begin: DateTime, end: DateTime, spontaneous: Boolean,
                 location: String, radius: Double) extends UsingImplicits[Events] {
	require(radius >= 0 && radius <= 500, "Event radius must be 0-500")
	require(begin <= end, "Event dates must verify 'begin <= end'")
}

//noinspection TypeAnnotation
class Events(tag: Tag) extends Table[Event](tag, "events") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def owner = column[Int]("owner")
	def desc = column[String]("desc")
	def begin = column[DateTime]("begin")
	def end = column[DateTime]("end")
	def spontaneous = column[Boolean]("spontaneous")
	def location = column[String]("location")
	def radius = column[Double]("radius")

	def * = (id, title, owner, desc, begin, end, spontaneous, location, radius) <> (Event.tupled, Event.unapply)
}

object Events extends TableQuery(new Events(_)) {
	implicit val EventFormat: Format[Event] = Json.format[Event]

	val findById = Events.findBy(_.id)
}
