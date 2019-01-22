/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.island.ohara.client

import java.net.HttpRetryException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * used to send http request to remote node. The operations implemented by this class includes 1) get, 2) delete, 3) put and 4) post.
  * There are many different kinds of error response so this class "expose" the error as an input type and you have to define the json
  * format of error response. Also, this class assume the error response should have a field - message - at least. The message is used to
  * generate a throwable exception.
  *
  */
private[client] trait HttpExecutor {
  //-------------------------------------------------[GET]-------------------------------------------------//
  def get[Res, E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[Res],
                                                     rm1: RootJsonFormat[E]): Future[Res]
  //-------------------------------------------------[DELETE]-------------------------------------------------//
  def delete[E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[E]): Future[Unit]
  def delete[Res, E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[Res],
                                                        rm1: RootJsonFormat[E]): Future[Res]
  //-------------------------------------------------[POST]-------------------------------------------------//
  def post[Req, Res, E <: HttpExecutor.Error](url: String, request: Req)(implicit rm0: RootJsonFormat[Res],
                                                                         rm1: RootJsonFormat[Req],
                                                                         rm2: RootJsonFormat[E]): Future[Res]

  /**
    * cluster apis use POST to add/remove node to/from a running cluster.
    * @param url url to cluster apis
    * @param rm0 format
    * @tparam Res response type
    * @return response
    */
  def post[Res, E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[Res],
                                                      rm1: RootJsonFormat[E]): Future[Res]

  //-------------------------------------------------[PUT]-------------------------------------------------//
  def put[Req, Res, E <: HttpExecutor.Error](url: String, request: Req)(implicit rm0: RootJsonFormat[Res],
                                                                        rm1: RootJsonFormat[Req],
                                                                        rm2: RootJsonFormat[E]): Future[Res]

  def put[Res, E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[Res],
                                                     rm1: RootJsonFormat[E]): Future[Res]
  def put[E <: HttpExecutor.Error](url: String)(implicit rm: RootJsonFormat[E]): Future[Unit]
  //-------------------------------------------------[CUSTOM]-------------------------------------------------//
  /**
    * If all default implementation of get, put, post and delete can't satisfy you, please build the request by by yourself.
    * @param request akka-http request. it should include http method and payload
    * @param rm0 format of response
    * @param rm1 format of error response
    * @tparam Res response type
    * @tparam E error type
    * @return response of exception
    */
  def request[Res, E <: HttpExecutor.Error](request: HttpRequest)(implicit rm0: RootJsonFormat[Res],
                                                                  rm1: RootJsonFormat[E]): Future[Res]
}

private[client] object HttpExecutor {

  /**
    * a basic error when remote node fails to handle your request. Probably not all restful server have implemented the error response. However,
    * we should always choose the "valid" restful server if we are valid programmer.
    */
  trait Error {

    /**
      * description of error
      * @return error message
      */
    def message: String
  }

  /**
    *  ActorSystem is a heavy component in akka, so we should reuse it as much as possible. We don't need to close it programmatically since
    *  it is a singleton object in whole jvm. And it will be released in closing jvm.
    */
  implicit lazy val SINGLETON: HttpExecutor = new HttpExecutor {
    private[this] implicit val actorSystem: ActorSystem = ActorSystem("Executor-SINGLETON")
    private[this] implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
    //-------------------------------------------------[PRIVATE]-------------------------------------------------//
    private[this] def unmarshal[T, E <: HttpExecutor.Error](res: HttpResponse)(implicit rm0: RootJsonFormat[T],
                                                                               rm1: RootJsonFormat[E]): Future[T] =
      if (res.status.isSuccess()) Unmarshal(res.entity).to[T]
      else asError(res)
    private[this] def unmarshal[E <: HttpExecutor.Error](res: HttpResponse)(
      implicit rm: RootJsonFormat[E]): Future[Unit] =
      if (res.status.isSuccess()) Future.successful[Unit](())
      else asError(res)

    private[this] def asError[E <: HttpExecutor.Error](res: HttpResponse)(implicit rm: RootJsonFormat[E]) =
      Unmarshal(res.entity).to[E].flatMap { error =>
        // TODO: Which code we should wrap to HttpRetryException?  by chia
        if (res.status.intValue() == StatusCodes.Conflict.intValue)
          Future.failed(new HttpRetryException(error.message, res.status.intValue()))
        else Future.failed(new IllegalArgumentException(error.message))
      }
    //-------------------------------------------------[GET]-------------------------------------------------//
    override def get[Res, E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[Res],
                                                                rm1: RootJsonFormat[E]): Future[Res] =
      Http().singleRequest(HttpRequest(HttpMethods.GET, url)).flatMap(unmarshal[Res, E])
    //-------------------------------------------------[DELETE]-------------------------------------------------//
    override def delete[E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[E]): Future[Unit] =
      Http().singleRequest(HttpRequest(HttpMethods.DELETE, url)).flatMap(unmarshal[E])
    override def delete[Res, E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[Res],
                                                                   rm1: RootJsonFormat[E]): Future[Res] =
      Http().singleRequest(HttpRequest(HttpMethods.DELETE, url)).flatMap(unmarshal[Res, E])
    //-------------------------------------------------[POST]-------------------------------------------------//
    override def post[Req, Res, E <: HttpExecutor.Error](
      url: String,
      request: Req)(implicit rm0: RootJsonFormat[Res], rm1: RootJsonFormat[Req], rm2: RootJsonFormat[E]): Future[Res] =
      Marshal(request).to[RequestEntity].flatMap { entity =>
        Http().singleRequest(HttpRequest(HttpMethods.POST, url, entity = entity)).flatMap(unmarshal[Res, E])
      }
    override def post[Res, E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[Res],
                                                                 rm1: RootJsonFormat[E]): Future[Res] =
      Http().singleRequest(HttpRequest(HttpMethods.POST, url)).flatMap(unmarshal[Res, E])
    //-------------------------------------------------[PUT]-------------------------------------------------//
    override def put[Req, Res, E <: HttpExecutor.Error](
      url: String,
      request: Req)(implicit rm0: RootJsonFormat[Res], rm1: RootJsonFormat[Req], rm2: RootJsonFormat[E]): Future[Res] =
      Marshal(request).to[RequestEntity].flatMap { entity =>
        Http().singleRequest(HttpRequest(HttpMethods.PUT, url, entity = entity)).flatMap(unmarshal[Res, E])
      }
    override def put[Res, E <: HttpExecutor.Error](url: String)(implicit rm0: RootJsonFormat[Res],
                                                                rm1: RootJsonFormat[E]): Future[Res] =
      Http().singleRequest(HttpRequest(HttpMethods.PUT, url)).flatMap(unmarshal[Res, E])
    override def put[E <: HttpExecutor.Error](url: String)(implicit rm: RootJsonFormat[E]): Future[Unit] =
      Http().singleRequest(HttpRequest(HttpMethods.PUT, url)).flatMap(unmarshal[E])
    //-------------------------------------------------[CUSTOM]-------------------------------------------------//
    override def request[Res, E <: HttpExecutor.Error](request: HttpRequest)(implicit rm0: RootJsonFormat[Res],
                                                                             rm1: RootJsonFormat[E]): Future[Res] =
      Http().singleRequest(request).flatMap(unmarshal[Res, E])
  }
}
