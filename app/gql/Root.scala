package gql

import models.{User, Users}
import sangria.schema.{Argument, Field, ListInputType, ListType, ObjectType, OptionInputType, OptionType, Schema, _}
import utils.SlickAPI._

trait Root extends Domain with Fetch {
	this: GraphQL =>
	val Root = ObjectType("Root", fields[Ctx, Unit](
		{ // users(id: , mail: , limit: , from: , friends: , nearby: )
			val Id = Argument("id", OptionInputType(ListInputType(IntType)))
			val Mail = Argument("mail", OptionInputType(ListInputType(StringType)))
			val Limit = Argument("limit", OptionInputType(IntType))
			val From = Argument("from", OptionInputType(IntType))
			val Friends = Argument("friends", OptionInputType(BooleanType))
			val Nearby = Argument("nearby", OptionInputType(CoordinatesType))
			val Radius = Argument("radius", OptionInputType(FloatType))
			Field("users", ListType(UserType),
				arguments = List(Id, Mail, Limit, From, Friends, Nearby, Radius),
				complexity = Some((ctx, args, childScore) => {
					val count = (args arg Id).map(_.length).getOrElse(0) + (args arg Mail).map(_.length).getOrElse(0)
					1 + (count min (args arg Limit).getOrElse(50)) * childScore
				}),
				resolve = (ctx) => {
					var query: Query[Users, User, Seq] = Users
					val from = (ctx arg From).getOrElse(0)
					val limit = (ctx arg Limit).getOrElse(50)
					query = (ctx arg Mail, ctx arg Id) match {
						case (Some(mails), Some(ids)) => query.filter(u => (u.mail inSet mails) || (u.id inSet ids))
						case (Some(mails), None) => query.filter(u => u.mail inSet mails)
						case (None, Some(ids)) => query.filter(u => u.id inSet ids)
						case (None, None) => query
					}
					ctx arg Nearby match {
						case Some(origin) =>
							ls.nearbyUsers(origin, (ctx arg Radius).getOrElse(50), limit, from).flatMap { users =>
								query.filter(_.id inSet users).run.map(list => list.map(u => (u.id, u)).toMap).map { data =>
									users.map(data.apply)
								}
							}
						case None => query.sortBy(_.id).drop(from).take(limit).run
					}
				})
		},
		{ // user(id: , mail: )
			val Id = Argument("id", OptionInputType(IntType))
			val Mail = Argument("mail", OptionInputType(StringType))
			Field("user", OptionType(UserType),
				arguments = List(Id, Mail),
				resolve = (ctx) => {
					var query: Query[Users, User, Seq] = Users
					for (id <- ctx arg Id) query = query.filter(_.id === id)
					for (mail <- ctx arg Mail) query = query.filter(_.mail === mail)
					query.headOption
				})
		}
	))

	val RootSchema = Schema.apply(Root)
}
