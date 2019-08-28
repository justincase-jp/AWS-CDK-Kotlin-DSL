import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

private lateinit var cdkVersionMap: MutableMap<String, List<Version>>
val updatedCdkVersions: Map<String, List<Version>>
    get() = cdkVersionMap.toMap()

private lateinit var latestVersionMap: MutableMap<String, Version>
val latestCrkVersions: Map<String, Version>
    get() = latestVersionMap.toMap()

@KtorExperimentalAPI
private val client = HttpClient(CIO)

@KtorExperimentalAPI
fun getCdkUpdatedVersions(): Map<String, List<Version>> = runBlocking {
    val latestGeneratedCdkVersions = cdkModuleList.associateWith { module ->
        val dslMavenMetadataUrl =
            "https://dl.bintray.com/justincase/aws-cdk-kotlin-dsl/jp/justincase/aws-cdk-kotlin-dsl/$module/maven-metadata.xml"
        async(context = Dispatchers.Default) {
            val response = withContext(Dispatchers.IO) {
                client.get<HttpResponse>(dslMavenMetadataUrl)
            }
            if (response.status != HttpStatusCode.OK) {
                return@async Version("0.0.0")
            }
            val doc = withContext(Dispatchers.IO) {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.content.toInputStream())
            }
            val lastVersionString = doc.getElementsByTagName("latest").item(0).textContent.split('-')[0]
            Version(lastVersionString)
        }
    }.mapValues { it.value.await() }
    latestVersionMap = latestGeneratedCdkVersions.toMutableMap()
    val cdkVersions = cdkModuleList.associateWith { module ->
        val cdkMavenMetadataUrl = "https://repo1.maven.org/maven2/software/amazon/awscdk/$module/maven-metadata.xml"
        async(context = Dispatchers.Default) {
            val response = withContext(Dispatchers.IO) {
                client.get<HttpResponse>(cdkMavenMetadataUrl)
            }
            val doc = withContext(Dispatchers.IO) {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.content.toInputStream())
            }
            doc.getElementsByTagName("versions").item(0).childNodes.asList().filter { it.nodeName == "version" }
                .map { Version(it.textContent) }
        }
    }.mapValues { pair -> pair.value.await().filter { it > latestGeneratedCdkVersions.getValue(pair.key) } }
    cdkVersionMap = cdkVersions.toMutableMap()
    cdkVersions
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
