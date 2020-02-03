import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import data.BintrayCreatePackageRequestJson
import data.ResponseJson
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
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

private const val requestUrl =
    "https://search.maven.org/solrsearch/select?q=g:software.amazon.awscdk&rows=200&wt=json&start=0"
private val apiBaseUrl = "https://api.bintray.com/packages/justincase/aws-cdk-kotlin-dsl"

lateinit var cdkModuleList: List<String>
    private set

val leastVersion = Version("1.0.0")

@KtorExperimentalAPI
fun getCdkModules(): List<String> {
    val client = HttpClient(CIO)
    val response = runBlocking { client.get<String>(requestUrl) }
    val obj = jacksonObjectMapper().readValue<ResponseJson>(response)
    cdkModuleList = obj.response.docs.filter {
        it.ec.containsAll(
            listOf(
                ".jar",
                ".pom"
            )
        ) && Version(it.latestVersion) >= leastVersion
    }.map { it.a }
    return cdkModuleList
}

/**
 * 必ず事前に[cdkModuleList]を呼び出しておくこと。
 * さもなくばエラー
 */
@KtorExperimentalAPI
fun createBintrayPackages(
    bintrayUser: String,
    bintrayApiKey: String
) = runBlocking {
    val client = HttpClient(CIO) {
        install(Auth) {
            basic {
                username = bintrayUser
                password = bintrayApiKey
            }
        }
        expectSuccess = false
    }

    cdkModuleList
        .toSet()
        .map {
            async {
                if (client.get<HttpStatusCode>("$apiBaseUrl/$it") != HttpStatusCode.OK) {
                    client.post<String>(apiBaseUrl) {
                        body = TextContent(
                            contentType = ContentType.Application.Json,
                            text = jacksonObjectMapper().writeValueAsString(
                                BintrayCreatePackageRequestJson(
                                    name = it,
                                    licenses = listOf("Apache-2.0"),
                                    vcsUrl = "https://github.com/justincase-jp/AWS-CDK-Kotlin-DSL"
                                )
                            )
                        )
                    }
                }
            }
        }
        .forEach { it.await() }
}
