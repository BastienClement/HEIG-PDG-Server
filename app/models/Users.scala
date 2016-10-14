package models

import utils.SlickAPI._

case class User(id: Int, name: String, mail: String, pass: String, rank: Int) {
	final def admin: Boolean = rank == 0
}

class Users(tag: Tag) extends Table[User](tag, "users") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def name = column[String]("name")
	def mail = column[String]("mail")
	def pass = column[String]("pass")
	def rank = column[Int]("rank")

	def * = (id, name, mail, pass, rank) <> (User.tupled, User.unapply)
}

object Users extends TableQuery(new Users(_))
