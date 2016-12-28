package services

import com.google.inject.{Inject, Singleton}
import java.util.NoSuchElementException
import models.{Friendship, Friendships, User, Users}
import play.api.cache.CacheApi
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
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
class FriendshipService @Inject() (cache: CacheApi)
                                  (implicit ec: ExecutionContext) {
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

	private def exists(a: Int, b: Int) = cache.getOrElse(cacheKey(a, b), 5.minutes) {
		Await.result(Friendships.exists(a, b).run, Duration.Inf)
	}

	/**
	  * Adds a new friendship between a and b.
	  *
	  * @param a the first user id
	  * @param b the second user id
	  * @return a future that will be completed once the database is updated
	  */
	def add(a: Int, b: Int): Future[_] = if (a > b) add(b, a) else {
		cache.remove(cacheKey(a, b))
		(Friendships += Friendship(a, b)).run
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

	def list(user: Int): Future[Seq[User]] = {
		Users.filter(other => Friendships.exists(user, other.id)).run
	}
}
