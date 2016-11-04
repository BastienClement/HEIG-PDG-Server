package models

import play.api.cache.CacheApi
import sangria.macros.derive.{GraphQLExclude, GraphQLField}
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.SlickAPI._
import utils.UsingImplicits

case class User(
		id: Int, firstname: String, lastname: String, username: String,
		mail: String, @GraphQLExclude pass: String, rank: Int) extends UsingImplicits[Users] {

	@GraphQLField
	final def admin: Boolean = rank == 0

	def location: (Double, Double) = ???
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
