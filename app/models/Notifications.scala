package models

import play.api.libs.json.{JsValue, Json, Writes}
import slick.lifted.TableQuery
import utils.SlickAPI._
import utils.{DateTime, UsingImplicits}

case class Notification(id: Long, recipient: Int, date: DateTime, tpe: String, payload: String)
		extends UsingImplicits[Notifications] {
	lazy val payloadObject: JsValue = Json.parse(payload)
}

//noinspection TypeAnnotation
class Notifications(tag: Tag) extends Table[Notification](tag, "notifications") {
	def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
	def recipient = column[Int]("recipient")
	def date = column[DateTime]("date")
	def tpe = column[String]("type")
	def payload = column[String]("payload")

	def * = (id, recipient, date, tpe, payload) <> (Notification.tupled, Notification.unapply)
}

object Notifications extends TableQuery(new Notifications(_)) {
	implicit val NotificationsWrites = new Writes[Notification] {
		def writes(n: Notification): JsValue = Json.obj(
			"type" -> n.tpe,
			"date" -> n.date,
			"payload" -> n.payloadObject
		)
	}
}
