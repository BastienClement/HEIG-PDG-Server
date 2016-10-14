package controllers.api

import play.api.mvc.Results

case class ApiException(sym: Symbol, status: Results#Status = Results.InternalServerError) extends Exception
