/*
 * Copyright (c) 2016 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package github4s.free.interpreters

import cats.data.Kleisli
import cats.implicits._
import cats.{ApplicativeError, Eval, MonadError, ~>}
import github4s.Config
import github4s.GithubDefaultUrls._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.HttpRequestBuilderExtension
import github4s.api.{Auth, Gists, Repos, Users}
import github4s.free.algebra._
import io.circe.Decoder
import simulacrum.typeclass

@typeclass
trait Capture[M[_]] {
  def capture[A](a: ⇒ A): M[A]
}

class Interpreters[M[_], C](implicit A: ApplicativeError[M, Throwable],
                            C: Capture[M],
                            httpClientImpl: HttpRequestBuilderExtension[C, M]) {

  type K[A] = Kleisli[M, Config, A]

  /**
    * Lifts Repository Ops to an effect capturing Monad such as Task via natural transformations
    */
  implicit def repositoryOpsInterpreter: RepositoryOps.Interpreter[K] =
    new RepositoryOps.Interpreter[K] {

      val repos = new Repos()

      def getRepoImpl(
          owner: String,
          repo: String
      ): K[GHResponse[Repository]] = Kleisli[M, Config, GHResponse[Repository]] { config =>
        repos.get(config.accessToken, config.headers, owner, repo)
      }

      def listCommitsImpl(
          owner: String,
          repo: String,
          sha: Option[String] = None,
          path: Option[String] = None,
          author: Option[String] = None,
          since: Option[String] = None,
          until: Option[String] = None,
          pagination: Option[Pagination] = None
      ): K[GHResponse[List[Commit]]] = Kleisli[M, Config, GHResponse[List[Commit]]] { config =>
        repos.listCommits(config.accessToken,
                          config.headers,
                          owner,
                          repo,
                          sha,
                          path,
                          author,
                          since,
                          until,
                          pagination)
      }

      def listContributorsImpl(
          owner: String,
          repo: String,
          anon: Option[String] = None
      ): K[GHResponse[List[User]]] = Kleisli[M, Config, GHResponse[List[User]]] { config =>
        repos.listContributors(config.accessToken, config.headers, owner, repo, anon)
      }
    }

  /**
    * Lifts User Ops to an effect capturing Monad such as Task via natural transformations
    */
  implicit def userOpsInterpreter: UserOps.Interpreter[K] =
    new UserOps.Interpreter[K] {

      val users = new Users()

      def getUserImpl(username: String): K[GHResponse[User]] =
        Kleisli[M, Config, GHResponse[User]] { config =>
          users.get(config.accessToken, config.headers, username)
        }

      def getAuthUserImpl: K[GHResponse[User]] =
        Kleisli[M, Config, GHResponse[User]] { config =>
          users.getAuth(config.accessToken, config.headers)
        }

      def getUsersImpl(since: Int,
                       pagination: Option[Pagination] = None): K[GHResponse[List[User]]] =
        Kleisli[M, Config, GHResponse[List[User]]] { config =>
          users.getUsers(config.accessToken, config.headers, since, pagination)
        }

    }

  /**
    * Lifts Auth Ops to an effect capturing Monad such as Task via natural transformations
    */
  implicit def authOpsInterpreter: AuthOps.Interpreter[K] =
    new AuthOps.Interpreter[K] {

      val auth = new Auth()

      def newAuthImpl(
          username: String,
          password: String,
          scopes: List[String],
          note: String,
          client_id: String,
          client_secret: String
      ): K[GHResponse[Authorization]] =
        Kleisli[M, Config, GHResponse[Authorization]] { config =>
          auth.newAuth(username, password, scopes, note, client_id, client_secret, config.headers)
        }

      def authorizeUrlImpl(
          client_id: String,
          redirect_uri: String,
          scopes: List[String]
      ): K[GHResponse[Authorize]] = Kleisli[M, Config, GHResponse[Authorize]] { config =>
        auth.authorizeUrl(client_id, redirect_uri, scopes)
      }

      def getAccessTokenImpl(
          client_id: String,
          client_secret: String,
          code: String,
          redirect_uri: String,
          state: String
      ): K[GHResponse[OAuthToken]] = Kleisli[M, Config, GHResponse[OAuthToken]] { config =>
        auth.getAccessToken(client_id, client_secret, code, redirect_uri, state, config.headers)
      }
    }

  /**
    * Lifts Gist Ops to an effect capturing Monad such as Task via natural transformations
    */
  implicit def gistOpsInterpreter: GistOps.Interpreter[K] =
    new GistOps.Interpreter[K] {

      val gists = new Gists()

      def newGistImpl(
          description: String,
          public: Boolean,
          files: Map[String, GistFile]
      ): K[GHResponse[Gist]] = Kleisli[M, Config, GHResponse[Gist]] { config =>
        gists.newGist(description, public, files, config.headers, config.accessToken)
      }

    }

}
