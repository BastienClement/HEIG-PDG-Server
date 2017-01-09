package models

import slick.lifted.TableQuery
import utils.SlickAPI._

case class Visit(event: Int, user: Int)

//noinspection TypeAnnotation
class Visits(tag: Tag) extends Table[Visit](tag, "visits") {
	def event = column[Int]("event_id", O.PrimaryKey)
	def user = column[Int]("user_id", O.PrimaryKey)

	def * = (event, user) <> (Visit.tupled, Visit.unapply)
}

object Visits extends TableQuery(new Visits(_)) {
	def find(event: Rep[Int], user: Rep[Int]): Query[Visits, Visit, Seq] = {
		Visits.filter(v => v.event === event && v.user === user)
	}

	def exists(event: Rep[Int], user: Rep[Int]): Rep[Boolean] = Visits.find(event, user).exists
}
