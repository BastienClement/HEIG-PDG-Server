package controllers.graphql

import models.{User, Users}
import sangria.schema._
import utils.SlickAPI._

trait Root extends Domain with Fetch {
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
				arguments = List(Id, Mail, Limit, From, Friends, Nearby),
				complexity = Some((ctx, args, childScore) => {
					val count = (args arg Id).map(_.length).getOrElse(0) + (args arg Mail).map(_.length).getOrElse(0)
					1 + (count min (args arg Limit).getOrElse(50)) * childScore
				}),
				resolve = (ctx) => {
					var query = Users.sortBy(_.id)
					for (from <- ctx arg From) query = query.filter(_.id > from)
					query = (ctx arg Mail, ctx arg Id) match {
						case (Some(mails), Some(ids)) => query.filter(u => (u.mail inSet mails) || (u.id inSet ids))
						case (Some(mails), None) => query.filter(u => u.mail inSet mails)
						case (None, Some(ids)) => query.filter(u => u.id inSet ids)
						case (None, None) => query
					}
					query.take((ctx arg Limit).getOrElse(50)).run
				})
		}, {
			val Id = Argument("id", OptionInputType(IntType))
			val Mail = Argument("mail", OptionInputType(StringType))
			Field("user", OptionType(User),
				arguments = List(Id, Mail),
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
