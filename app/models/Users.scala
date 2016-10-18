package models

import play.api.cache.CacheApi
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.UsingImplicits
import utils.SlickAPI._

case class User(
		id: Int, firstname: String, lastname: String, username: String,
		mail: String, pass: String, rank: Int) extends UsingImplicits[Users] {
	final def admin: Boolean = rank == 0
}

class Users(tag: Tag) extends Table[User](tag, "users") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def firstname = column[String]("firstname")
	def lastname = column[String]("lastname")
	def username = column[String]("username")
	def mail = column[String]("mail")
	def pass = column[String]("pass")
	def rank = column[Int]("rank")

	def * = (id, firstname, lastname, username, mail, pass, rank) <> (User.tupled, User.unapply)
}

object Users extends TableQuery(new Users(_)) {
	def findById(id: Int)(implicit cache: CacheApi): Future[User] = {
		cache.getOrElse(s"users.$id", 5.minutes) {
			Users.filter(_.id === id).head
		}
	}
}
