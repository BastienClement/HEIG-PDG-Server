package services

import com.google.inject.Inject
import models.{User, Users}
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.{ExecutionContext, Future}
import utils.Coordinates
import utils.SlickAPI._

class UserService @Inject() (implicit ec: ExecutionContext) {
	def get(id: Int): Future[User] = {
		Users.findById(id).head
	}

	def setCurrentActiveDevice(id: Int, cad: String): Future[_] = {
		val cadHash = Some(DigestUtils.sha1Hex(cad))
		Users.findById(id).map(u => u.cad).update(cadHash).run
	}

	def updateLocation(id: Int, coords: Coordinates, cad: String): Future[Boolean] = {
		val cadHash = Some(DigestUtils.sha1Hex(cad))
		val userQuery = Users.findById(id).filter(u => u.cad === cad || u.cad.isEmpty)
		userQuery.map(user => (user.lat, user.lon, user.cad)).update((coords.lat, coords.lon, cadHash)).run.map {
			case 0 => false
			case 1 => true
		}
	}
}
