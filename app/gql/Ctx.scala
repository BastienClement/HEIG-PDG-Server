package gql

import models.User

case class Ctx(user: Option[User])
