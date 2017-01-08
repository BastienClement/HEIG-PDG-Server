package services

import com.google.inject.{Inject, Singleton}
import models.{Notification, Notifications, Users}
import org.apache.commons.codec.digest.DigestUtils
import play.api.cache.CacheApi
import play.api.libs.json.{JsNull, JsValue}
import scala.concurrent.{ExecutionContext, Future}
import utils.DateTime
import utils.DateTime.Units
import utils.SlickAPI._

@Singleton
class NotificationsService @Inject() (cache: CacheApi)
                                     (implicit ec: ExecutionContext) {
	/**
	  * Fetches pending notification for the given user.
	  *
	  * Notifications returned by this method are removed from the database.
	  *
	  * @param user the user for which notifications should be fetched
	  * @param cad  the user current device, must match the currently active device
	  * @return the list of pending notifications for the user
	  */
	def fetch(user: Int, cad: String): Future[Seq[Notification]] = {
		val cadHash = DigestUtils.sha1Hex(cad)
		Users.findById(user).filter(_.cad === cadHash).exists.result.flatMap {
			case true =>
				Notifications
						.filter(n => n.recipient === user)
						.sortBy(n => n.date.asc)
						.result
						.flatMap { notifications =>
							if (notifications.nonEmpty) {
								val cleanup = Notifications.filter(n => n.id inSet notifications.map(_.id)).delete
								cleanup andThen DBIO.successful(notifications)
							} else {
								DBIO.successful(notifications)
							}
						}
			case false =>
				DBIO.failed(new IllegalStateException())
		}.transactionally.run
	}

	/**
	  * Sends a new notification to the given user.
	  *
	  * In addition to registering the notification, this method also perform basic cleanup
	  * of pending notifications for the user: any notification after the 50 most recent ones
	  * or older than 7 days are removed from the database even if not yet delivered.
	  *
	  * @param user    the user id
	  * @param tpe     the notification type string
	  * @param payload the payload object
	  * @return a future that will be completed once the operation is completed
	  */
	def send(user: Int, tpe: String, payload: JsValue = JsNull): Future[_] = {
		val insert = Notifications += Notification(0, user, DateTime.now, tpe, payload.toString())
		val cleanup = Notifications.filter { n =>
			val overflow = Notifications.filter(m => m.recipient === user).sortBy(_.date.desc).drop(50).map(_.id)
			(n.id in overflow) || n.date < (DateTime.now - 7.days)
		}.delete
		(insert andThen cleanup).run
	}
}
