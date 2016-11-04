package controllers.graphql

import controllers.api.ApiRequest
import models.User
import play.api.mvc.AnyContent
import sangria.macros.derive._

trait Domain { this: GraphQLController =>
	type Ctx = ApiRequest[AnyContent]

	val User = deriveObjectType[Ctx, User]()
}
