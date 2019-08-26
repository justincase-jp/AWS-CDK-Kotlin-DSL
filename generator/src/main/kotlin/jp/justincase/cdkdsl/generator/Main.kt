package jp.justincase.cdkdsl.generator

import com.google.common.reflect.ClassPath
import java.io.File

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "application argument is required" }
    main(File(if (args.size >= 2) args[1] else "generated"), args[0])
}

fun main(targetDir: File, moduleName: String) {
    val cdkClasses = ClassPath.from(ClassLoader.getSystemClassLoader()).allClasses.asSequence()
        .filter { it.packageName.startsWith("software.amazon.awscdk") }
        .map { it.load() }

    genResourceConstructResource(cdkClasses, targetDir, moduleName)
}