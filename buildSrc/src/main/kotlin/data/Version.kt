package data

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