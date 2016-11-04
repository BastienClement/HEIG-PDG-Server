package controllers.graphql

import models.{User, Users}
import sangria.schema._
import utils.SlickAPI._

trait Root extends Domain {
	this: GraphQLController =>
	val Root = ObjectType[Ctx, Unit](
		"Root", fields[Ctx, Unit]({
			val Id = Argument("id", OptionInputType(ListInputType(IntType)))
			val Mail = Argument("mail", OptionInputType(ListInputType(StringType)))
			val Limit = Argument("limit", OptionInputType(IntType))
			val From = Argument("from", OptionInputType(IntType))
			val Friends = Argument("friends", OptionInputType(BooleanType))
			val Nearby = Argument("nearby", OptionInputType(BooleanType))
			Field("users", ListType(User),
				arguments = Limit :: From :: Friends :: Nearby :: Nil,
				resolve = (ctx) => {
					var query = Users.sortBy(_.id)
					for (from <- ctx arg From) query = query.filter(_.id > from)
					query.take((ctx arg Limit).getOrElse(50) min 100).run
				})
		}, {
			val Id = Argument("id", OptionInputType(IntType))
			val Mail = Argument("mail", OptionInputType(StringType))
			Field("user", OptionType(User),
				arguments = Id :: Mail :: Nil,
				resolve = (ctx) => {
					var query: Query[Users, User, Seq] = Users
					for (id <- ctx arg Id) query = query.filter(_.id === id)
					for (mail <- ctx arg Mail) query = query.filter(_.mail === mail)
					query.headOption
				})
		}
		)
	)

	val RootSchema = Schema.apply(Root)
}
