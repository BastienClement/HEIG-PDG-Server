package services

import com.google.inject.{Inject, Singleton}
import models.{User, Users}
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.{ExecutionContext, Future}
import utils.{Coordinates, DateTime}
import utils.SlickAPI._

@Singleton
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
		val data = (coords.lat, coords.lon, cadHash, DateTime.now)
		userQuery.map(user => (user.lat, user.lon, user.cad, user.updated)).update(data).run.map {
			case 0 => false
			case 1 => true
		}
	}

	/**
	  * Searches nearby users.
	  *
	  * By default, only friends are returned. If the `all` parameter is
	  * set to true, even non-friend users are searched. Note that this
	  * require an administrator PointOfView, otherwise this parameter is
	  * ignored.
	  *
	  * @param point  the point around which users are searched
	  * @param radius the search radius (in meters)
	  * @param all    whether non-friend users should also be searched
	  * @param pov    the point of view
	  * @return a list of nearby users and their distance (in meters) from the given point
	  */
	def nearby(point: Coordinates, radius: Double, all: Boolean = false)
	          (implicit pov: Users.PointOfView): Future[Seq[(User, Double)]] = {
		val (lat, lon) = Coordinates.unpack(point)
		sql"""
			SELECT *, earth_distance(ll_to_earth(${lat}, ${lon}), ll_to_earth(lat, lon)) AS dist
			FROM users
			WHERE earth_box(ll_to_earth(${lat}, ${lon}), ${radius}) @> ll_to_earth(lat, lon)
				AND earth_distance(ll_to_earth(${lat}, ${lon}), ll_to_earth(lat, lon)) <= ${radius}
				AND (${all && pov.admin} OR
					EXISTS(
						SELECT * FROM friends WHERE (a = ${pov.user.id} AND b = id) OR (a = id AND b = ${pov.user.id})
					))
			ORDER BY dist ASC""".as[(User, Double)].run
	}
}
