package data

import com.fasterxml.jackson.annotation.JsonProperty

data class BintrayCreatePackageRequestJson(
    @JsonProperty("custom_licenses")
    val customLicenses: List<String> = listOf(),
    @JsonProperty("desc")
    val desc: String = "",
    @JsonProperty("github_release_notes_file")
    val githubReleaseNotesFile: String = "",
    @JsonProperty("github_repo")
    val githubRepo: String = "",
    @JsonProperty("issue_tracker_url")
    val issueTrackerUrl: String = "",
    @JsonProperty("labels")
    val labels: List<String> = listOf(),
    @JsonProperty("licenses")
    val licenses: List<String> = listOf(),
    @JsonProperty("name")
    val name: String = "",
    @JsonProperty("public_download_numbers")
    val publicDownloadNumbers: Boolean = false,
    @JsonProperty("public_stats")
    val publicStats: Boolean = false,
    @JsonProperty("vcs_url")
    val vcsUrl: String = "",
    @JsonProperty("website_url")
    val websiteUrl: String = ""
)