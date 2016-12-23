package services

import com.google.inject.Inject
import models.{User, Users}
import scala.concurrent.{ExecutionContext, Future}
import utils.Coordinates
import utils.SlickAPI._

class UserService @Inject() (implicit ec: ExecutionContext) {
	def get(id: Int): Future[User] = {
		Users.findById(id).head
	}

	def updateLocation(id: Int, coords: Coordinates): Future[_] = {
		Users.findById(id).map(user => (user.lat, user.lon)).update((coords.lat, coords.lon)).run
	}
}
