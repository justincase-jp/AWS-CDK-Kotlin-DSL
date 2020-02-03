import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.xml.parsers.DocumentBuilderFactory

@UseExperimental(io.ktor.util.KtorExperimentalAPI::class)
private val client = HttpClient(CIO)

lateinit var moduleDependencyMap: Map<String, List<String>>
    private set

@UseExperimental(io.ktor.util.KtorExperimentalAPI::class)
suspend fun getModuleDependencies(): Map<String, List<String>> {
    return if (::moduleDependencyMap.isInitialized) moduleDependencyMap else withContext(Dispatchers.Default) {
        cdkLatestVersions.keys.asFlow().map { module ->
            val version = cdkLatestVersions.getValue(module).toString()
            val targetUrl =
                "https://central.maven.org/maven2/software/amazon/awscdk/$module/$version/$module-${
                version}.pom"
            val doc = withContext(Dispatchers.IO) {
                val response = client.get<HttpResponse>(targetUrl)

                check(response.status == HttpStatusCode.OK) { "${response.status} on accessing $targetUrl" }
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.content.toInputStream())
            }
            val list = doc.getElementsByTagName("dependency").asList().map { node ->
                PomArtifact(
                    node.childNodes.asList().single { it.nodeName == "groupId" }.textContent,
                    node.childNodes.asList().single { it.nodeName == "artifactId" }.textContent,
                    node.childNodes.asList().single { it.nodeName == "version" }.textContent
                )
            }.filter { it.groupId == "software.amazon.awscdk" }.map { it.artifactId }
            module to list
        }.toList().toMap().apply { moduleDependencyMap = this }
    }
}

fun getModuleDependenciesBlocking() = runBlocking { getModuleDependencies() }

data class PomArtifact(
    val groupId: String,
    val artifactId: String,
    val version: String
)
