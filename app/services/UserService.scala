package services

import com.google.inject.{Inject, Singleton}
import models.{User, Users}
import org.apache.commons.codec.digest.DigestUtils
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext, Future}
import utils.DateTime.Units
import utils.SlickAPI._
import utils.{BCrypt, Coordinates, DateTime, Patch}

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
		val cadHash = DigestUtils.sha1Hex(cad)
		val userQuery = Users.findById(id).filter(u => u.cad === cadHash || u.cad.isEmpty)
		val data = (coords.lat, coords.lon, Some(cadHash), DateTime.now)
		userQuery.map(user => (user.lat, user.lon, user.cad, user.updated)).update(data).run.map {
			case 0 => false
			case 1 => true
		}
	}

	def promote(id: Int, rank: Int): Future[Boolean] = {
		Users.findById(id).map(_.rank).update(rank).run.map {
			case 1 => true
			case 0 => false
		}
	}

	/**
	  * Patches a user.
	  *
	  * @param id
	  * @param patch
	  * @return
	  */
	def patch(id: Int, patch: JsObject): Future[User] = {
		Patch(Users.findById(id))
				.MapField("username", _.username)
				.MapField("firstname", _.firstname)
				.MapField("lastname", _.lastname)
				.MapField("mail", _.mail)
				.Map(d => (d \ "password").asOpt[String].map(p => BCrypt.hashpw(p, BCrypt.gensalt())), _.pass)
				.Execute(patch)
	}

	/**
	  * Searches for users matching the given query.
	  *
	  * This method only searches for non-friend users, also excluding the user itself.
	  * At most 50 results will be returned, sorted by increasing distance from the current location.
	  *
	  * @param q   the query string
	  * @param pov the point of view
	  * @return a list of users matching the query
	  */
	def search(q: String)
	          (implicit pov: Users.PointOfView): Future[Seq[User]] = {
		val (lat, lon) = pov.user.location.getOrElse(Coordinates(46.5197, 6.6323)).unpack
		val pattern = q.replaceAll("_|%", "").toLowerCase + "%"
		sql"""
			SELECT *, earth_distance(ll_to_earth(${lat}, ${lon}), ll_to_earth(lat, lon)) AS dist
			FROM users
			WHERE id != ${pov.user.id} AND (LOWER(username) LIKE ${pattern} OR LOWER(mail) LIKE ${pattern})
				AND NOT EXISTS (SELECT * FROM friends WHERE (a = ${pov.user.id} AND b = id) OR (a = id AND b = ${pov.user.id}))
			ORDER BY dist ASC
			LIMIT 50""".as[User].run
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
		val expiration = (DateTime.now - 55.minutes).toTimestamp
		sql"""
			SELECT *, earth_distance(ll_to_earth(${lat}, ${lon}), ll_to_earth(lat, lon)) AS dist
			FROM users
			WHERE earth_box(ll_to_earth(${lat}, ${lon}), ${radius}) @> ll_to_earth(lat, lon)
				AND earth_distance(ll_to_earth(${lat}, ${lon}), ll_to_earth(lat, lon)) <= ${radius}
				AND updated > ${expiration}
				AND (${all && pov.admin} OR
					EXISTS(
						SELECT * FROM friends WHERE (a = ${pov.user.id} AND b = id) OR (a = id AND b = ${pov.user.id})
					))
			ORDER BY dist ASC
			LIMIT 100""".as[(User, Double)].run
	}
}
