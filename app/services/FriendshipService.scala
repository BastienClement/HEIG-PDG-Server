package services

import com.google.inject.{Inject, Singleton}
import java.util.NoSuchElementException
import models._
import play.api.cache.CacheApi
import play.api.libs.json.Json
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success
import utils.DateTime
import utils.SlickAPI._

/**
  * Friendship management service.
  *
  * This service offers a synchronous way to retrieve friendship status
  * between two users. This is necessary since the status is used to
  * validate access to user's properties during UserViews serialization.
  *
  * The database model requires friendship records to be ordered: the
  * lowest id user come first. However, this service will ensure that
  * the users are correctly ordered before issuing database operation,
  * accepting inputs in any order.
  *
  * The service stores friendship status in the memory-cache to prevent
  * more than one database lookup every 5 minutes. It is thus important
  * that every friendship manipulation operations are done using this
  * service to keep cache and database in sync.
  *
  * @param cache the Play cache API
  */
@Singleton
class FriendshipService @Inject() (cache: CacheApi, ns: NotificationsService)
                                  (implicit ec: ExecutionContext) {
	/** Implicit self value */
	private implicit val implicitSelf = this

	/**
	  * Generates the cache key for the friendship status between a and b.
	  *
	  * @param a the first user's id
	  * @param b the second user's id
	  * @return the cache key used to store the friendship status
	  */
	private def cacheKey(a: Int, b: Int): String = s"friendship/$a/$b"

	/**
	  * Checks whether `a` and `b` are friends.
	  *
	  * @param a the first user id
	  * @param b the second user id
	  * @return true if a and b are friends, false otherwise
	  */
	def friends(a: Int, b: Int): Boolean = if (a > b) friends(b, a) else exists(a, b)

	/** Internal implementation of the exists check with caching mechanism */
	private def exists(a: Int, b: Int) = cache.getOrElse(cacheKey(a, b), 5.minutes) {
		Await.result(Friendships.exists(a, b).run, Duration.Inf)
	}

	/**
	  * Lists friends.
	  *
	  * @param user the user whose friends should be listed
	  */
	def list(user: Int): Future[Seq[User]] = {
		Users.filter(other => Friendships.exists(user, other.id)).run
	}

	/**
	  * Lists pending friendship requests.
	  *
	  * @param user the user whose friends should be listed
	  */
	def requests(user: Int): Future[Seq[(User, DateTime)]] = {
		(for {
			request <- FriendRequests.filter(r => r.recipient === user).sortBy(r => r.date.desc)
			user <- Users.findById(request.sender)
		} yield (user, request.date)).run
	}

	/**
	  * Sends a friendship request.
	  *
	  * @param sender    the sender user
	  * @param recipient the recipient user
	  * @return a future that will be resolved with true if the operation succedeed,
	  *         false otherwise
	  */
	def request(sender: Int, recipient: Int): Future[Boolean] = {
		(FriendRequests.between(sender, recipient).exists || Friendships.exists(sender, recipient)).result.flatMap {
			case false =>
				FriendRequests += FriendRequest(sender, recipient)
			case true =>
				throw new IllegalStateException()
		}.transactionally.run.map(_ => true).recover { case _ => false } andThen {
			case Success(true) =>
				val senderUser = Users.findById(sender).head
				val recipientUser = Users.findById(recipient).head
				for (s <- senderUser; r <- recipientUser) {
					implicit val pov = new Users.PointOfView(r)
					ns.send(recipient, "FRIEND_REQUEST", Json.toJson(s))
				}
		}
	}

	/**
	  * Accepts a friendship request.
	  *
	  * @param recipient the recipient user
	  * @param sender    the sender user
	  * @return a future that will be resolved with true if the operation succedeed,
	  *         false otherwise
	  */
	def accept(recipient: Int, sender: Int): Future[Boolean] = {
		val a = if (recipient < sender) recipient else sender
		val b = if (recipient < sender) sender else recipient

		FriendRequests.findByKey(sender, recipient).delete.flatMap {
			case 1 => Friendships += Friendship(a, b)
			case 0 => throw new IllegalStateException()
		}.transactionally.run.map { _ =>
			cache.remove(cacheKey(a, b))
			true
		}.recover {
			case _ => false
		}
	}

	/**
	  * Declines a friendship request.
	  *
	  * @param recipient the recipient user
	  * @param sender    the sender user
	  * @return a future that will be resolved with true if the operation succedeed,
	  *         false otherwise
	  */
	def decline(recipient: Int, sender: Int): Future[Boolean] = {
		FriendRequests.findByKey(sender, recipient).delete.run.map {
			case 1 => true
			case 0 => false
		}
	}

	/**
	  * Removes a friendship between a and b.
	  *
	  * @param a the first user id
	  * @param b the second user id
	  * @return a future that will be completed once the database is updated
	  */
	def remove(a: Int, b: Int): Future[_] = if (a > b) remove(b, a) else {
		cache.remove(cacheKey(a, b))
		Friendships.find(a, b).delete.run.map {
			case 0 => throw new NoSuchElementException
			case _ => ()
		}
	}
}
