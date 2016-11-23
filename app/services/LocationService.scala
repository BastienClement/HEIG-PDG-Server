package services

import com.google.inject.{Inject, Singleton}
import models.User
import play.api.libs.json.{JsObject, Json}
import scala.concurrent.{ExecutionContext, Future}
import utils.Coordinates

@Singleton
class LocationService @Inject() (es: ElasticSearch)(implicit val ec: ExecutionContext) {
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

	def locationForUser(user: Int): Future[Option[Coordinates]] = {
		es.get(s"/users/user/$user").map { res =>
			(res.json \ "_source" \ "location").asOpt[Coordinates]
		}
	}

	def nearbyUsers(coords: Coordinates, radius: Double, limit: Int = 50, from: Int = 0): Future[Seq[Int]] = {
		es.post("/users/user/_search", Json.obj(
			"_source" -> Json.arr("id"),
			"query" -> Json.obj(
				"bool" -> Json.obj(
					"must" -> Json.obj(
						"match_all" -> Json.obj()
					),
					"filter" -> Json.obj(
						"geo_distance" -> Json.obj(
							"distance" -> s"${radius}km",
							"location" -> coords
						)
					)
				)
			),
			"sort" -> Json.arr(
				Json.obj(
					"_geo_distance" -> Json.obj(
						"location" -> coords,
						"order" -> "asc",
						"unit" -> "km",
						"distance_type" -> "plane"
					)
				)
			),
			"size" -> limit,
			"from" -> from
		)).map { res =>
			(res.json \ "hits"  \ "hits").as[Seq[JsObject]].map(hit => (hit \ "_source" \ "id").as[Int])
		}
	}
}
