package utils

import models.User
import services.FriendshipService

/**
  * A point of view from which a User is viewed.
  *
  * @param user the user viewing the other user
  * @param fs   the friendship service
  */
class PointOfView private (val user: User)(implicit fs: FriendshipService) {
	/** Whether the point of view is an admin user. */
	def admin: Boolean = user.admin

	/** Whether the point of view is from the target itself. */
	def self(target: User): Boolean = user.id == target.id

	/** Whether the point of view is an admin or friend user. */
	def friend(target: User): Boolean = admin || target.id == user.id || fs.friends(user.id, target.id)
}

object PointOfView {
	def forUser(user: User)(implicit fs: FriendshipService): PointOfView = new PointOfView(user)

	private val John = User(0, "John", "Doe", "Anonymous", "john@doe.com", 1, 0, 0, DateTime.epoch)

	object AnonymousPointOfView extends PointOfView(John)(null) {
		override def admin: Boolean = false
		override def self(target: User): Boolean = false
		override def friend(target: User): Boolean = false
	}

	def anonymous: PointOfView = AnonymousPointOfView
}

