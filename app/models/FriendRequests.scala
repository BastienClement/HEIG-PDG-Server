package models

import slick.lifted.TableQuery
import utils.DateTime
import utils.SlickAPI._

case class FriendRequest(sender: Int, recipient: Int, date: DateTime = DateTime.now)

//noinspection TypeAnnotation
class FriendRequests(tag: Tag) extends Table[FriendRequest](tag, "requests") {
	def sender = column[Int]("sender", O.PrimaryKey)
	def recipient = column[Int]("recipient", O.PrimaryKey)
	def date = column[DateTime]("date")

	def * = (sender, recipient, date) <> (FriendRequest.tupled, FriendRequest.unapply)
}

object FriendRequests extends TableQuery(new FriendRequests(_)) {
	def findByKey(sender: Rep[Int], recipient: Rep[Int]) = {
		FriendRequests.filter(r => r.sender === sender && r.recipient === recipient)
	}

	def between(a: Rep[Int], b: Rep[Int]) = {
		FriendRequests.filter(r => (r.sender === a && r.recipient === b) || (r.sender === b && r.recipient === a))
	}
}
