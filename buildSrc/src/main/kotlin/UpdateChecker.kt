import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

private lateinit var unhandledCdkVersionMap: Map<String, List<Version>>
val cdkLatestUnhandledVersions: Map<String, List<Version>>
    get() = unhandledCdkVersionMap.toMap()

private lateinit var latestCdkVersionMap: Map<String, Version>
val cdkLatestVersions: Map<String, Version>
    get() = latestCdkVersionMap.toMap()

private lateinit var latestVersionMap: MutableMap<String, Version>
val latestDependedCdkVersions: Map<String, Version>
    get() = latestVersionMap.toMap()

@KtorExperimentalAPI
private val client = HttpClient(CIO)

@KtorExperimentalAPI
fun getCdkUpdatedVersions(): Map<String, List<Version>> = runBlocking {
    val latestGeneratedCdkVersions = cdkModuleList.asFlow()
        .map { module ->
            val dslMavenMetadataUrl =
                "https://dl.bintray.com/justincase/aws-cdk-kotlin-dsl/jp/justincase/aws-cdk-kotlin-dsl/$module/maven-metadata.xml"
            val response = withContext(Dispatchers.IO) {
                client.get<HttpResponse>(dslMavenMetadataUrl)
            }
            if (response.status != HttpStatusCode.OK) {
                return@map module to leastVersion
            }
            val doc = withContext(Dispatchers.IO) {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.content.toInputStream())
            }
            val lastVersionString = doc.getElementsByTagName("latest").item(0).textContent.split('-')[0]
            module to Version(lastVersionString)
        }.toList().toMap()
    latestVersionMap = latestGeneratedCdkVersions.toMutableMap()
    val cdkVersions = cdkModuleList.asFlow()
        .map { module ->
            val cdkMavenMetadataUrl = "https://repo1.maven.org/maven2/software/amazon/awscdk/$module/maven-metadata.xml"
            val response = withContext(Dispatchers.IO) {
                client.get<HttpResponse>(cdkMavenMetadataUrl)
            }
            val doc = withContext(Dispatchers.IO) {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.content.toInputStream())
            }
            module to doc.getElementsByTagName("versions").item(0).childNodes.asList()
                .filter { it.nodeName == "version" }
                .map { Version(it.textContent) }
        }.toList().toMap()
    latestCdkVersionMap = cdkVersions.mapValues { pair -> pair.value.last() }
    unhandledCdkVersionMap = cdkVersions.mapValues { pair ->
        pair.value.filter { it > latestGeneratedCdkVersions.getValue(pair.key) }
    }
    unhandledCdkVersionMap
}

fun NodeList.asList(): List<Node> {
    val list = mutableListOf<Node>()
    for (i in 1..this.length) {
        list.add(item(i - 1))
    }
    return list
}

inline class Version(val version: String) : Comparable<Version> {

    val major
        get() = version.split('-')[0].split('.')[0].trimMargin().toInt()

    val minor
        get() = version.split('-')[0].split('.').getOrNull(1)?.trimMargin()?.toInt() ?: 0

    val revision
        get() = version.split('-')[0].split('.').getOrNull(2)?.trimMargin()?.toInt() ?: 0

    val post
        get() = version.removePrefix("$major.$minor.$revision")

    override fun compareTo(other: Version): Int {
        fun exception() = IllegalStateException("Unexpected return value of Int.compareTo()")
        return when (major.compareTo(other.major)) {
            1 -> 1
            -1 -> -1
            0 -> when (minor.compareTo(other.minor)) {
                1 -> 1
                -1 -> -1
                0 -> when (revision.compareTo(other.revision)) {
                    1 -> 1
                    -1 -> -1
                    0 -> post.compareTo(other.post)
                    else -> throw exception()
                }
                else -> throw exception()
            }
            else -> throw exception()
        }
    }

    override fun toString(): String {
        return version
    }
}
