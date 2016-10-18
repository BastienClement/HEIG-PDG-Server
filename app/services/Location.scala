package services

import com.google.inject.{Inject, Singleton}
import models.User
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Location @Inject()(es: ElasticSearch)(implicit val ec: ExecutionContext) {
	def updateUser(user: User, lat: Double, lon: Double): Future[Unit] = {
		val uid = user.id
		es.post(s"/users/user/$uid", Json.obj(
			"id" -> uid,
			"firstname" -> user.firstname,
			"lastname" -> user.lastname,
			"username" -> user.username,
			"mail" -> user.mail,
			"location" -> Json.arr(lon, lat)
		)).map { _ => () }
	}
}
