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
	def find(x: Rep[Int], y: Rep[Int]): Query[Friendships, Friendship, Seq] = {
		val a = Case If (x < y) Then x Else y
		val b = Case If (x < y) Then y Else x
		Friendships.filter(fs => fs.a === a && fs.b === b).take(1)
	}

	def exists(a: Rep[Int], b: Rep[Int]): Rep[Boolean] = Friendships.find(a, b).exists
}
