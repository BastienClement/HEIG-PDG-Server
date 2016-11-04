package controllers.graphql

import controllers.api.ApiRequest
import models.User
import play.api.mvc.AnyContent
import sangria.schema._

trait Domain { this: GraphQLController =>
	type Ctx = ApiRequest[AnyContent]

	def filter[V, R](pred: Context[Ctx, V] => Boolean)(value: Context[Ctx, V] => R): Context[Ctx, V] => Option[R] =
		(ctx: Context[Ctx, V]) => if (pred(ctx)) Some(value(ctx)) else None

	val User = ObjectType("User",
		fields[Ctx, User](
			Field("id", IntType, resolve = _.value.id),
			Field("firstname", StringType, resolve = _.value.firstname),
			Field("lastname", StringType, resolve = _.value.lastname),
			Field("username", StringType, resolve = _.value.username),
			Field("mail", OptionType(StringType), resolve = env => {
				env.ctx.userOpt.collect { case user if user.admin => env.value.mail }
			}),
			Field("rank", OptionType(IntType), resolve = env => {
				env.ctx.userOpt.collect { case user if user.admin => env.value.rank }
			}),
			Field("admin", BooleanType, resolve = _.value.admin),
			Field("location", OptionType(ListType(FloatType)), resolve = env => {
				env.value.location.map {
					case Some((a, b)) => Some(Seq(a, b))
					case _ => None
				}
			})
		)
	)
}
