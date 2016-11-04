package controllers.graphql

import models.Users
import sangria.execution.deferred.{DeferredResolver, Fetcher}

trait Fetch extends Domain {
	this: GraphQLController =>

	val users = Fetcher((ctx: Ctx, ids: Seq[Int]) => Users.findByIds(ids))

	val resolver = DeferredResolver.fetchers(users)
}
