import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readText
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

private val cdkVersionMap: MutableMap<String, List<Version>> = mutableMapOf()
val updatedCdkVersions: Map<String, List<Version>> = cdkVersionMap.toMap()

@KtorExperimentalAPI
private val client = HttpClient(CIO)

@KtorExperimentalAPI
fun getCdkUpdatedVersions(module: String): List<Version> {
    fun getDslMavenMetadataUrl() =
        "https://dl.bintray.com/justincase/aws-cdk-kotlin-dsl/jp/justincase/aws-cdk-kotlin-dsl/$module/maven-metadata.xml"

    fun getCdkMavenMetadataUrl() = "https://repo1.maven.org/maven2/software/amazon/awscdk/$module/maven-metadata.xml"

    fun getLatestGeneratedCdkVersion(): Version {
        val response = runBlocking { client.get<HttpResponse>(getDslMavenMetadataUrl()) }
        if (response.status != HttpStatusCode.OK) {
            return Version("0.0.0")
        }
        val xmlString = runBlocking { response.readText() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlString.byteInputStream())
        val lastVersionString = doc.getElementsByTagName("latest").item(0).textContent.split('-')[0]
        return Version(lastVersionString)
    }

    fun getCdkVersionList(): List<Version> {
        val xmlString = runBlocking { client.get<String>(getCdkMavenMetadataUrl()) }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlString.byteInputStream())
        return doc.getElementsByTagName("versions").item(0).childNodes.asList().filter { it.nodeName == "version" }
            .map { Version(it.textContent) }
    }

    val last = getLatestGeneratedCdkVersion()
    val cdkVersionList = getCdkVersionList()
    val list = cdkVersionList.filter { it > last }
    cdkVersionMap[module] = list
    return list
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
