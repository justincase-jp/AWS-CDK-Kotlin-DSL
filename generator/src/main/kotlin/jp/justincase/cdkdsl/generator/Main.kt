package jp.justincase.cdkdsl.generator

import com.google.common.reflect.ClassPath
import java.io.File

fun main(args: Array<String>) {
    main(File(if (args.isNotEmpty()) args[0] else "generated"))
}

fun main(targetDir: File) {
    val cdkClasses = ClassPath.from(ClassLoader.getSystemClassLoader()).allClasses.asSequence()
        .filter { it.packageName.startsWith("software.amazon.awscdk") }
        .map { it.load() }

    genResourceConstructResource(cdkClasses, targetDir)
}