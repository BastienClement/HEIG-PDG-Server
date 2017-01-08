package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import play.api.Application
import play.api.mvc.Controller
import services.NotificationsService

/**
  * Notifications controllers.
  *
  * Allow the client to fetch pending notifications.
  * This is an easy alternative to using a true push notification system.
  *
  * @param ns  the notification service
  * @param app an instance of the Play application
  */
@Singleton
class NotificationsController @Inject() (ns: NotificationsService)
                                        (val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	/** Fetches pending notification for the client */
	def notifications = AuthApiAction.async { implicit req =>
		ns.fetch(req.user.id, req.token).map(list => Ok(list)).orElse(Conflict)
	}
}
