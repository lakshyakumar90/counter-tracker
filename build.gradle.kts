plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("checkFormatting") {
    group = "verification"
    description = "Checks Kotlin and Gradle source for common formatting violations."

    val sourceFiles = fileTree(rootDir) {
        include("**/*.kt", "**/*.kts", "**/*.toml")
        exclude("**/build/**", "**/.gradle/**")
    }
    inputs.files(sourceFiles)

    doLast {
        val violations = sourceFiles.files.flatMap { source ->
            val text = source.readText(Charsets.UTF_8)
            val lineViolations = text.split('\n').mapIndexedNotNull { index, rawLine ->
                val line = rawLine.removeSuffix("\r")
                when {
                    line.endsWith(' ') -> "${source.relativeTo(rootDir)}:${index + 1}: trailing spaces"
                    line.endsWith('\t') -> "${source.relativeTo(rootDir)}:${index + 1}: trailing tab"
                    '\t' in line -> "${source.relativeTo(rootDir)}:${index + 1}: tab indentation"
                    else -> null
                }
            }
            if (text.isNotEmpty() && !text.endsWith('\n')) {
                lineViolations + "${source.relativeTo(rootDir)}: missing final newline"
            } else {
                lineViolations
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException("Formatting check failed:\n${violations.joinToString("\n")}")
        }
    }
}
