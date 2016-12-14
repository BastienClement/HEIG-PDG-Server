package models

import models.Users.UserView
import play.api.libs.json._
import sangria.execution.deferred.HasId
import scala.concurrent.Future
import services.LocationService
import utils.SlickAPI._
import utils.{Coordinates, UsingImplicits}

case class User(
		id: Int, firstname: String, lastname: String, username: String,
		mail: String, pass: String, rank: Int) extends UsingImplicits[Users] {

	def admin: Boolean = rank == Users.Rank.Admin
	def location(implicit ls: LocationService): Future[Option[Coordinates]] = ls.locationForUser(id)
	lazy val view = new UserView(this)
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
	implicit val UserHasId = HasId[User, Int](_.id)

	object Rank {
		final val Admin = 0
		final val User = 3
		final val Restricted = 5
		final val Banned = 10
	}

	class UserView(val user: User) {
		private def filter[T](valid: User => Boolean)(value: => T)(implicit other: User): Option[T] = {
			if (valid(other)) Some(value) else None
		}

		private val otherAdmin = (other: User) => other.admin
		private val otherFriend = (other: User) => other.admin || ???

		def id: Int = user.id
		def username: String = user.username
		def firstname(implicit other: User): Option[String] = filter(otherFriend) { user.lastname }
		def lastname(implicit other: User): Option[String] = filter(otherFriend) { user.lastname }
		def mail(implicit other: User): Option[String] = filter(otherAdmin) { user.mail }
		def rank(implicit other: User): Option[Int] = filter(otherAdmin) { user.rank }
		def admin: Boolean = user.rank == Rank.Admin
		def location(implicit other: User, ls: LocationService): Future[Option[Coordinates]] = {
			filter(otherFriend) { user.location }.getOrElse(Future.successful(None))
		}
	}

	implicit def UserViewFormat(implicit user: User): Writes[UserView] = new Writes[UserView] {
		def writes(u: UserView): JsValue = Json.obj(
			"id" -> u.id,
			"username" -> u.username,
			"firstname" -> u.firstname,
			"lastname" -> u.lastname,
			"mail" -> u.mail,
			"admin" -> u.admin
		)
	}

	def findById(id: Int): Query[Users, User, Seq] = Users.filter(_.id === id)
	def findById(ids: Seq[Int]): Query[Users, User, Seq] = Users.filter(_.id inSet ids)
}
