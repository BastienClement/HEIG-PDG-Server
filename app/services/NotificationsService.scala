package services

import com.google.inject.{Inject, Singleton}
import models.{Notification, Notifications, Users}
import org.apache.commons.codec.digest.DigestUtils
import play.api.cache.CacheApi
import play.api.libs.json.JsValue
import scala.concurrent.{ExecutionContext, Future}
import utils.DateTime
import utils.DateTime.Units
import utils.SlickAPI._

@Singleton
class NotificationsService @Inject() (cache: CacheApi)
                                     (implicit ec: ExecutionContext) {
	def fetch(user: Int, cad: String): Future[Seq[Notification]] = {
		val cadHash = DigestUtils.sha1Hex(cad)
		Users.findById(user).filter(_.cad === cadHash).exists.result.flatMap {
			case true =>
				val query = Notifications.filter(n => n.recipient === user)
				query.sortBy(n => n.date.asc).result.flatMap { notifications =>
					if (notifications.nonEmpty) query.delete andThen DBIO.successful(notifications)
					else DBIO.successful(notifications)
				}
			case false =>
				DBIO.failed(new IllegalStateException())
		}.transactionally.run
	}

	def send(user: Int, tpe: String, payload: JsValue): Future[_] = {
		val insert = Notifications += Notification(0, user, DateTime.now, tpe, payload.toString())
		val cleanup = Notifications.filter { n =>
			val overflow = Notifications.filter(m => m.recipient === user).sortBy(_.date.desc).drop(50).map(_.id)
			(n.id in overflow) || n.date < (DateTime.now - 7.days)
		}.delete
		(insert andThen cleanup).run
	}
}
