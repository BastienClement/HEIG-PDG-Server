package models

import play.api.libs.json.{JsObject, Json}
import utils.SlickAPI._

case class User(id: Int, name: String, mail: String, pass: String) {
	def toJson: JsObject = Json.obj("id" -> id, "name" -> name, "mail" -> mail)
}

class Users(tag: Tag) extends Table[User](tag, "users") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def name = column[String]("name")
	def mail = column[String]("mail")
	def pass = column[String]("pass")

	def * = (id, name, mail, pass) <> (User.tupled, User.unapply)
}

object Users extends TableQuery(new Users(_))
