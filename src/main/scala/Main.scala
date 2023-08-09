import cats.effect._
import com.github.markusbernhardt.proxy.ProxySearch
import org.http4s.client.Client
import org.http4s.jdkhttpclient.JdkHttpClient
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.http4s.Http4sBackend
import sttp.model.Uri
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.json.SttpUpickleApiExtension.{asJsonSnake, upickleBodySerializerSnake}
import sttp.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.openai.requests.completions.chat.{Message, Role}

import java.net.ProxySelector
import scala.util.chaining._

object Main extends IOApp {
  val chatRequestBody: ChatBody = ChatBody(
    model = ChatCompletionModel.CustomChatCompletionModel("vicuna-7b-v1.3"),
    messages = Seq(
      Message(
        role = Role.User,
        content = "Hello!"
      )
    )
  )

  def createChatCompletion(baseUri: Uri, authToken: String, chatBody: ChatBody): Request[Either[OpenAIException, ChatResponse]] =
    basicRequest.auth
      .bearer(authToken)
      .post(baseUri.addPath("chat", "completions"))
      .body(chatBody)
      .response(asJsonSnake[ChatResponse])

  def backendFromClient[F[_] : Async](client: Client[F]): StreamBackend[F, Fs2Streams[F]] =
    Http4sBackend.usingClient(Client[F] { request =>
      Resource.eval(request.toStrict(None)).flatMap(client.run)
    })

  override def run(args: List[String]): IO[ExitCode] = {
    ProxySelector.setDefault(
      Option(new ProxySearch().tap { s =>
        s.addStrategy(ProxySearch.Strategy.JAVA)
        s.addStrategy(ProxySearch.Strategy.ENV_VAR)
      }.getProxySelector)
        .getOrElse(ProxySelector.getDefault)
    )

    for {
      client <- JdkHttpClient.simple[IO]
      backend = backendFromClient(client)
      response <- createChatCompletion(uri"https://example.com/v1", "<api-key>", chatRequestBody)
        .send(backend)
        .map(_.body)
      _ = println(response)
    } yield ExitCode.Success
  }
}
