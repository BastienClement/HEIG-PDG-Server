package services

import com.google.inject.Inject
import models.{User, Users}
import play.api.cache.CacheApi
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Failure
import utils.SlickAPI._

class UserService @Inject() (cache: CacheApi, es: ElasticSearch)(implicit ec: ExecutionContext) {
	def findById(id: Int): Future[User] = cache.getOrElse(s"users/$id", 5.minutes) {
		Users.findById(id).head andThen {
			case Failure(_) => cache.remove(s"users/$id")
		}
	}
}
