import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

const val requestUrl = "http://search.maven.org/solrsearch/select?q=g:software.amazon.awscdk&rows=200&wt=json&start=0"
private val apiBaseUrl = "https://api.bintray.com/packages/justincase/aws-cdk-kotlin-dsl"

@KtorExperimentalAPI
fun createBintrayPackages(
    bintrayUser: String,
    bintrayApiKey: String
) {
    val client = HttpClient(CIO) {
        install(Auth) {
            basic {
                username = bintrayUser
                password = bintrayApiKey
            }
        }
    }
    val response = runBlocking { client.get<String>(requestUrl) }
    val obj = jacksonObjectMapper().readValue<ResponseJson>(response)
    val packageList = obj.response.docs.filter { it.ec.containsAll(listOf(".jar", ".pom")) }.map { it.a }
    val requests = packageList.associateWith {
        GlobalScope.async {
            client.get<HttpStatusCode>("$apiBaseUrl/$it")
        }
    }.mapValues {
        runBlocking { it.value.await() }
    }.filterValues { it != HttpStatusCode.OK }.keys.map {
        GlobalScope.async {
            client.post<String>(apiBaseUrl) {
                //header("Content-Type", "application/json; charset=UTF8")
                body = TextContent(
                    jacksonObjectMapper().writeValueAsString(
                        BintrayCreatePackageRequestJson(
                            name = it,
                            licenses = listOf("Apache-2.0"),
                            vcsUrl = "https://github.com/justincase-jp/AWS-CDK-Kotlin-DSL"
                        )
                    ), contentType = ContentType.Application.Json
                )
            }
        }
    }
    runBlocking {
        requests.forEach { it.await() }
    }
}