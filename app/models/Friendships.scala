package models

import slick.lifted.TableQuery
import utils.SlickAPI._

case class Friendship(a: Int, b: Int) {
	require(a < b)
}

class Friendships(tag: Tag) extends Table[Friendship](tag, "friends") {
	def a = column[Int]("a", O.PrimaryKey)
	def b = column[Int]("b", O.PrimaryKey)

	def * = (a, b) <> (Friendship.tupled, Friendship.unapply)
}

object Friendships extends TableQuery(new Friendships(_)) {
	def exists(a: Rep[Int], b: Rep[Int]): Rep[Boolean] = {
		Friendships.filter(f => (f.a === a && f.b === b) || (f.a === b && f.b === a)).exists
	}
}
