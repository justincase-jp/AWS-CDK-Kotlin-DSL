package data

import com.fasterxml.jackson.annotation.JsonProperty

data class ResponseJson(
    @JsonProperty("response")
    val response: ResponseJson = ResponseJson(),
    @JsonProperty("responseHeader")
    val responseHeader: ResponseHeaderJson = ResponseHeaderJson(),
    @JsonProperty("spellcheck")
    val spellcheck: SpellcheckJson = SpellcheckJson()
) {
    data class ResponseHeaderJson(
        @JsonProperty("params")
        val params: ParamsJson = ParamsJson(),
        @JsonProperty("QTime")
        val qTime: Int = 0,
        @JsonProperty("status")
        val status: Int = 0
    ) {
        data class ParamsJson(
            @JsonProperty("core")
            val core: String = "",
            @JsonProperty("fl")
            val fl: String = "",
            @JsonProperty("indent")
            val indent: String = "",
            @JsonProperty("q")
            val q: String = "",
            @JsonProperty("rows")
            val rows: String = "",
            @JsonProperty("sort")
            val sort: String = "",
            @JsonProperty("spellcheck")
            val spellcheck: String = "",
            @JsonProperty("spellcheck.count")
            val spellcheckcount: String = "",
            @JsonProperty("start")
            val start: String = "",
            @JsonProperty("version")
            val version: String = "",
            @JsonProperty("wt")
            val wt: String = ""
        )
    }

    data class SpellcheckJson(
        @JsonProperty("suggestions")
        val suggestions: List<Any> = listOf()
    )

    data class ResponseJson(
        @JsonProperty("docs")
        val docs: List<DocJson> = listOf(),
        @JsonProperty("numFound")
        val numFound: Int = 0,
        @JsonProperty("start")
        val start: Int = 0
    ) {
        data class DocJson(
            @JsonProperty("a")
            val a: String = "",
            @JsonProperty("ec")
            val ec: List<String> = listOf(),
            @JsonProperty("g")
            val g: String = "",
            @JsonProperty("id")
            val id: String = "",
            @JsonProperty("latestVersion")
            val latestVersion: String = "",
            @JsonProperty("p")
            val p: String = "",
            @JsonProperty("repositoryId")
            val repositoryId: String = "",
            @JsonProperty("text")
            val text: List<String> = listOf(),
            @JsonProperty("timestamp")
            val timestamp: Long = 0,
            @JsonProperty("versionCount")
            val versionCount: Int = 0
        )
    }
}