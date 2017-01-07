package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.{ApiActionBuilder, ApiException, ApiRequest}
import models.{User, Users}
import play.api.Application
import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.Controller
import scala.concurrent.Future
import scala.util.Try
import services.{FriendshipService, UserService}
import utils.SlickAPI._

/**
  * The controller handing user-related operations.
  *
  * @param users an instance of the UserService
  * @param app   the Play application instance
  */
@Singleton
class UsersController @Inject() (val users: UserService, val friends: FriendshipService)
                                (val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	/**
	  * Extracts the numeric user ID from the given URI parameter.
	  *
	  * @param uid      the URI parameter
	  * @param selfOnly only allow the "self" keyword and the user's own ID
	  * @param strict   enforce the selfOnly restriction even for administrators
	  * @param req      the request object
	  * @tparam A the body type of the request
	  * @return the corresponding numeric user ID
	  */
	protected def userId[A](uid: String, selfOnly: Boolean = false, strict: Boolean = false)
	                       (implicit req: ApiRequest[A]): Int = uid match {
		case "self" => req.user.id
		case _ =>
			val id = Try(uid.toInt).getOrElse(throw ApiException('USER_INVALID_UID, UnprocessableEntity))
			if (!selfOnly || req.userOpt.exists(u => u.id == id || (!strict && u.admin))) id
			else throw ApiException('SELF_ONLY_ACTION, Forbidden)
	}

	/**
	  * Executes the action if the given user parameter is the caller itself.
	  * Otherwise, the 'USERS_ACTION_SELF_ONLY error is thrown.
	  *
	  * @param uid    the URI parameter
	  * @param action the action to execute
	  * @param req    the request object
	  * @tparam A the body type of the request
	  * @tparam T the return type of the action
	  * @return the return value of the action
	  */
	protected def requireSelf[A, T](uid: String)
	                               (action: User => T)
	                               (implicit req: ApiRequest[A]): T = {
		userId(uid, selfOnly = true, strict = true)
		action(req.user)
	}

	/**
	  * Returns the list of users matching the given filters.
	  */
	def list = AuthApiAction.async { implicit req =>
		Users.sortBy(u => u.id).run.map { users =>
			Ok(Json.toJson(users))
		}
	}

	/**
	  * Returns user information.
	  *
	  * @param user the user id or the keyword "self"
	  */
	def user(user: String) = AuthApiAction.async { implicit req =>
		users.get(userId(user)).map(u => Ok(u)).orElse(NotFound('USER_NOT_FOUND))
	}

	/**
	  * Returns user rank value.
	  *
	  * @param user the user id or the keyword "self"
	  */
	def rank(user: String) = AuthApiAction.async { implicit req =>
		if (!req.user.admin) Future.successful(Forbidden('ADMIN_ACTION_RESTRICTED))
		else users.get(userId(user)).map(u => Ok(u.rank)).orElse(NotFound('USERS_USER_NOT_FOUND))
	}

	/**
	  * Returns user rank value.
	  *
	  * @param user the user id or the keyword "self"
	  */
	def promote(user: String) = AuthApiAction.async(parse.tolerantJson) { implicit req =>
		if (!req.user.admin) Future.successful(Forbidden('ADMIN_ACTION_RESTRICTED))
		else users.promote(userId(user), req.body.as[Int]).replace(NoContent).orElse(NotFound('USER_NOT_FOUND))
	}

	/**
	  * Updates user information.
	  *
	  * @param user the user id ot the keyword "self"
	  */
	def patch(user: String) = AuthApiAction.async(parse.tolerantJson) { implicit req =>
		users.patch(userId(user, selfOnly = true), req.body.as[JsObject]).map(u => Ok(u))
	}

	/**
	  * Updates user location.
	  */
	def location = AuthApiAction.async { implicit req =>
		val lat = param[Double]("lat")
		val lon = param[Double]("lon")
		users.updateLocation(req.user.id, (lat, lon), req.token).map {
			case true => NoContent
			case false => Conflict
		}
	}

	def nearby(lat: Double, lon: Double, radius: Double, all: Boolean) = AuthApiAction.async { implicit req =>
		users.nearby((lat, lon), radius, all).map { users =>
			Ok(users.map { case (user, distance) =>
				Json.toJson(user).as[JsObject] + ("distance" -> JsNumber(distance))
			})
		}
	}

	def search(q: String) = AuthApiAction.async { implicit req =>
		users.search(q).map(results => Ok(results))
	}

	def friendsList(uid: String) = AuthApiAction.async { implicit req =>
		friends.list(userId(uid)).map(friends => Ok(friends))
	}

	def friendRequests = AuthApiAction.async { implicit req =>
		friends.requests(req.user.id).map { requests =>
			Ok(requests.map { case (user, date) =>
				Json.obj("user" -> user, "date" -> date)
			})
		}
	}

	def friendSend(other: Int) = AuthApiAction.async { implicit req =>
		if (other == req.user.id) throw ApiException('USER_FRIEND_SELF_REQUEST, BadRequest)
		friends.request(req.user.id, other).map {
			case true => NoContent
			case false => Conflict('USER_FRIEND_DUPLICATE)
		}
	}

	def friendAccept(other: Int) = AuthApiAction.async { implicit req =>
		friends.accept(req.user.id, other).map {
			case true => NoContent
			case false => NotFound('USER_FRIEND_NO_REQUEST)
		}
	}

	def friendDecline(other: Int) = AuthApiAction.async { implicit req =>
		friends.decline(req.user.id, other).map {
			case true => NoContent
			case false => NotFound('USER_FRIEND_NO_REQUEST)
		}
	}

	def friendRemove(other: Int) = AuthApiAction.async { implicit req =>
		friends.remove(req.user.id, other).replace(NoContent).orElse(NotFound)
	}
}
