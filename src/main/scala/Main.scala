import cats.effect._
import cats.syntax.option._
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
import scala.sys.env
import scala.util.chaining._

object Main extends IOApp {
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

  trait AI {
    def query(request: String): IO[String]
  }

  object AI {
    def apply(backend: Backend[IO], openAiBaseUrl: Uri, openAiApiKey: String): AI = new AI {
      override def query(request: String): IO[String] =
        createChatCompletion(openAiBaseUrl, openAiApiKey, ChatBody(
          model = ChatCompletionModel.CustomChatCompletionModel("vicuna-7b-v1.3"),
          messages = Seq(
            Message(
              role = Role.User,
              content = request
            )
          )
        ))
          .send(backend)
          .map(_.body.toTry.get.choices.headOption.map(_.message.content).orEmpty)
    }
  }

  val jsonExample: String =
    """{
      |  "device": "<device name>",
      |  "state": true/false
      |}""".stripMargin

  val query =
    s"You are a smart home assistant. For a query you generate a json that matches the following example:\n$jsonExample\nYou don't explain your response and just output the raw json.\nTurn off the test light."

  override def run(args: List[String]): IO[ExitCode] = {
    ProxySelector.setDefault(
      Option(new ProxySearch().tap { s =>
        s.addStrategy(ProxySearch.Strategy.JAVA)
        s.addStrategy(ProxySearch.Strategy.ENV_VAR)
      }.getProxySelector)
        .getOrElse(ProxySelector.getDefault)
    )

    val openAiBaseUrl: Uri = Uri.unsafeParse(env.getOrElse("OPENAI_BASE_URL", "https://api.openai.com/v1"))
    val openAiApiKey: String = env.getOrElse("OPENAI_API_KEY", throw new RuntimeException("Missing OPENAI_API_KEY!"))

    for {
      client <- JdkHttpClient.simple[IO]
      backend = backendFromClient(client)
      ai = AI(backend, openAiBaseUrl, openAiApiKey)
      response <- ai.query(query)
      _ = println(response)
    } yield ExitCode.Success
  }
}
