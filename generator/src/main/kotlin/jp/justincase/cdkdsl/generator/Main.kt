package jp.justincase.cdkdsl.generator

import com.google.common.reflect.ClassPath
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import java.io.File

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "application argument is required" }
    main(File(if (args.size >= 2) args[1] else "generated"), args[0])
}

fun main(targetDir: File, moduleName: String) {
    val cdkClasses = ClassPath.from(ClassLoader.getSystemClassLoader()).allClasses.asSequence()
        .filter { it.packageName.startsWith("software.amazon.awscdk") }
        .map { it.load() }

    val srcDir = File(targetDir, "src/main/kotlin").also { if (!it.exists()) it.mkdirs() }
    genResourceConstructResource(cdkClasses, srcDir, moduleName)
}

fun getFileSpecBuilder(fileName: String, moduleName: String): FileSpec.Builder =
    FileSpec.builder("jp.justincase.cdkdsl", fileName).apply {
        addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "FunctionName, Unused").build())
        addAnnotation(
            AnnotationSpec.builder(JvmName::class).addMember(
                "%S",
                "ResourceConstructDsl${moduleName.capitalize()}"
            ).build()
        )
    }
