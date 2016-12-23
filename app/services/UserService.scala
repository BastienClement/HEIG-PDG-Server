package services

import com.google.inject.Inject
import models.{User, Users}
import scala.concurrent.{ExecutionContext, Future}
import utils.SlickAPI._

class UserService @Inject() (implicit ec: ExecutionContext) {
	def get(id: Int): Future[User] = {
		Users.findById(id).head
	}
}
