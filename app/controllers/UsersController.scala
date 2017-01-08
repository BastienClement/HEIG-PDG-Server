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
  * The controller handing user-related and friendship management operations.
  *
  * @param users an instance of the UserService
  * @param app   the Play application instance
  */
@Singleton
class UsersController @Inject() (users: UserService, friends: FriendshipService)
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
	  * This action is only available to administrator users.
	  *
	  * @param user the user id or the keyword "self"
	  */
	def rank(user: String) = AuthApiAction.async { implicit req =>
		if (!req.user.admin) Future.successful(Forbidden('ADMIN_ACTION_RESTRICTED))
		else users.get(userId(user)).map(u => Ok(u.rank)).orElse(NotFound('USERS_USER_NOT_FOUND))
	}

	/**
	  * Updates the user rank.
	  *
	  * This action is only available to administrators users.
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

	/**
	  * Searches for users around a given location.
	  *
	  * By default, only friends are returned by this endpoint.
	  * Administrators have the option to use the `all` option to ask for the
	  * list of every user in the search radius, friends or not.
	  *
	  * @param lat    the latitude of the center of the search radius
	  * @param lon    the longitude of the center of the search radius
	  * @param radius the search radius in meters
	  * @param all    whether to search for all users, not just friend; requires admin
	  */
	def nearby(lat: Double, lon: Double, radius: Double, all: Boolean) = AuthApiAction.async { implicit req =>
		users.nearby((lat, lon), radius, all).map { users =>
			Ok(users.map { case (user, distance) =>
				Json.toJson(user).as[JsObject] + ("distance" -> JsNumber(distance))
			})
		}
	}

	/**
	  * Searches for users matching the given query.
	  * This method only searches for non-friend users.
	  *
	  * @param q the search query, matching both the username and the email address
	  */
	def search(q: String) = AuthApiAction.async { implicit req =>
		users.search(q).map(results => Ok(results))
	}

	/**
	  * Fetches the friend list of the given user.
	  *
	  * @param uid the user whose list should be returned
	  */
	def friendsList(uid: String) = AuthApiAction.async { implicit req =>
		friends.list(userId(uid)).map(friends => Ok(friends))
	}

	/**
	  * Fetches the list of pending incoming friendship requests for the current user.
	  */
	def friendRequests = AuthApiAction.async { implicit req =>
		friends.requests(req.user.id).map { requests =>
			Ok(requests.map { case (user, date) =>
				Json.obj("user" -> user, "date" -> date)
			})
		}
	}

	/**
	  * Sends a friendship request to another user.
	  *
	  * This step is required before the other user can accept the friendship request,
	  * to create the actual friendship relation between both user.
	  *
	  * @param other the user to which the request should be sent
	  */
	def friendSend(other: Int) = AuthApiAction.async { implicit req =>
		if (other == req.user.id) throw ApiException('USER_FRIEND_SELF_REQUEST, BadRequest)
		friends.request(req.user.id, other).map {
			case true => NoContent
			case false => Conflict('USER_FRIEND_DUPLICATE)
		}
	}

	/**
	  * Accepts a friendship request.
	  *
	  * This operation requires that a pending friendship request from the other user
	  * to the current user exists. The pending request will be removed and the friend
	  * relationship will be recorded.
	  *
	  * @param other the user from whose request should be accepted
	  */
	def friendAccept(other: Int) = AuthApiAction.async { implicit req =>
		friends.accept(req.user.id, other).map {
			case true => NoContent
			case false => NotFound('USER_FRIEND_NO_REQUEST)
		}
	}

	/**
	  * Declines a friendship request.
	  *
	  * @param other the user from whose the request should be declined
	  */
	def friendDecline(other: Int) = AuthApiAction.async { implicit req =>
		friends.decline(req.user.id, other).map {
			case true => NoContent
			case false => NotFound('USER_FRIEND_NO_REQUEST)
		}
	}

	/**
	  * Removes a currently existing friendship relation.
	  *
	  * @param other the user who should be removed from the friends list
	  */
	def friendRemove(other: Int) = AuthApiAction.async { implicit req =>
		friends.remove(req.user.id, other).replace(NoContent).orElse(NotFound)
	}
}
