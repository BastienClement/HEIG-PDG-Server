package gql

import sangria.execution.deferred.DeferredResolver

trait Fetch {
	this: GraphQL =>

	val resolver = DeferredResolver.fetchers()
}
