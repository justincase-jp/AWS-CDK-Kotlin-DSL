
import cats.data.Kleisli
import cats.effect.{IO, IOApp}
import org.http4s.Method.PUT
import org.http4s.Response
import org.http4s.Status.{Conflict, Ok}
import org.http4s.dsl.request._
import org.http4s.syntax.literals._
import org.typelevel.ci._
import porterie.syntax.literal._
import porterie.syntax.uri._
import porterie.{Porterie, forwarded}

object GitHubPackagesProxy extends IOApp {
  override
  def run(args: List[String]): IO[Nothing] =
    Porterie[IO](
      38877,
      forwarded[IO](_ withBaseUri https"://maven.pkg.github.com/justincase-jp/AWS-CDK-Kotlin-DSL").map {
        case r @ PUT -> _ ~ extension if (extension match {
          case "pom" | "module" | "jar" => false
          case _ => true
        }) =>
          // Skip all checksum uploads (We don't know the validity of this checksum)
          r.withUri(uri"https://devnull-as-a-service.com/dev/null")
        case r =>
          r.removeHeader(ci"Host")
      },
      Kleisli.fromFunction[IO, Response[IO]] {
        case r @ Response(Conflict, _, _, _, _) =>
          // Ignore all conflicts
          r.withStatus(Ok)
        case r =>
          r
      }
    ).start(
      runtime.compute
    )
}
