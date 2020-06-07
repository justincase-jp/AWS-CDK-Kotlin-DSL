import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import data.BintrayCreatePackageRequestJson
import data.PomArtifact
import data.ResponseJson
import data.Version
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

object PackageManager {

    private const val requestUrl =
        "https://search.maven.org/solrsearch/select?q=g:software.amazon.awscdk&rows=200&wt=json&start=0"

    private const val bintrayApiBaseUrl = "https://api.bintray.com/packages/justincase/aws-cdk-kotlin-dsl"

    @UseExperimental(KtorExperimentalAPI::class)
    private val client = HttpClient(CIO)

    private val leastVersion = Version("1.20.0")

    val cdkModules: Set<String> by lazy {
        val response = runBlocking { client.get<String>(requestUrl) }
        val obj = jacksonObjectMapper().readValue<ResponseJson>(response)
        obj.response.docs.filter {
            it.ec.containsAll(
                listOf(
                    ".jar",
                    ".pom"
                )
            ) && Version(it.latestVersion) >= leastVersion
        }.map { it.a }.filter { "monocdk" !in it }.toSet()
    }

    val latestGeneratedCdkVersions: Map<String, Version> by lazy {
        val job = GlobalScope.async {
            cdkModules.asFlow()
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
                        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                            .parse(response.content.toInputStream())
                    }
                    val lastVersionString = doc.getElementsByTagName("latest").item(0).textContent.split('-')[0]
                    module to Version(lastVersionString)
                }.toList().toMap()
        }
        runBlocking {
            job.await()
        }
    }

    val cdkVersions: Map<String, List<Version>> by lazy {
        runSuspend {
            cdkModules.asFlow()
                .map { module ->
                    val cdkMavenMetadataUrl =
                        "https://repo1.maven.org/maven2/software/amazon/awscdk/$module/maven-metadata.xml"
                    val response = withContext(Dispatchers.IO) {
                        client.get<HttpResponse>(cdkMavenMetadataUrl)
                    }
                    val doc = withContext(Dispatchers.IO) {
                        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                            .parse(response.content.toInputStream())
                    }
                    module to doc.getElementsByTagName("versions").item(0).childNodes.asList()
                        .filter { it.nodeName == "version" }
                        .map { Version(it.textContent) }
                }.toList().toMap()
        }
    }

    val cdkModulesForVersion: Map<Version, Set<String>> by lazy {
        val map = mutableMapOf<Version, MutableSet<String>>()
        cdkVersions.forEach { (module, versions) ->
            versions.forEach {
                if (map.containsKey(it)) {
                    map.getValue(it) += module
                } else {
                    map[it] = mutableSetOf(module)
                }
            }
        }
        map
    }

    val latestCdkVersions: Map<String, Version> by lazy {
        cdkVersions.mapValues { pair -> pair.value.last() }
    }

    val modulesForLatestCdkVersions: Pair<Version, Set<String>> by lazy {
        val map = mutableMapOf<Version, MutableSet<String>>()
        latestCdkVersions.forEach { (module, version) ->
            if (map.containsKey(version)) {
                map[version]!! += module
            } else {
                map[version] = mutableSetOf(module)
            }
        }
        map.toSortedMap().run { lastKey() to getValue(lastKey()) }
    }

    val unhandledCdkVersions: Map<String, List<Version>> by lazy {
        cdkVersions.mapValues { pair ->
            pair.value.filter { it > latestGeneratedCdkVersions.getValue(pair.key) }
        }
    }

    val unhandledCdkModulesForVersions: Map<Version, Set<String>> by lazy {
        val map = mutableMapOf<Version, MutableSet<String>>()
        unhandledCdkVersions.forEach { (module, versions) ->
            versions.forEach {
                if (map.containsKey(it)) {
                    map[it]!! += module
                } else {
                    map[it] = mutableSetOf(module)
                }
            }
        }
        map
    }

    val moduleDependencyMap: Map<String, List<String>> by lazy {
        runSuspend {
            latestCdkVersions.keys.asFlow().map { module ->
                val version = latestCdkVersions.getValue(module).toString()
                val targetUrl =
                    "https://repo1.maven.org/maven2/software/amazon/awscdk/$module/$version/$module-${
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
            }.toList().toMap()
        }
    }

    @UseExperimental(KtorExperimentalAPI::class)
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
            expectSuccess = false
        }
        runSuspend {
            cdkModules.map {
                async {
                    if (client.get<HttpStatusCode>("$bintrayApiBaseUrl/$it") != HttpStatusCode.OK) {
                        client.post<String>(bintrayApiBaseUrl) {
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
                        println("Package $it is created")
                    }
                }
            }.forEach { it.await() }
        }
    }

    private fun <T> runSuspend(block: suspend CoroutineScope.() -> T): T {
        return GlobalScope.async(block = block).let { async -> runBlocking { async.await() } }
    }

    private fun NodeList.asList(): List<Node> {
        val list = mutableListOf<Node>()
        for (i in 1..this.length) {
            list.add(item(i - 1))
        }
        return list
    }
}
