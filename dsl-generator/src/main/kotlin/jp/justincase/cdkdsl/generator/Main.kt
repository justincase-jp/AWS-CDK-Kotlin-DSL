package jp.justincase.cdkdsl.generator

import com.google.common.reflect.ClassPath
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import java.io.File

/**
 * You can implement your own generator.
 * Create object implements [ICdkDslGenerator], add it to this, then run [main].
 */
val generators = mutableListOf(
    ResourceConstructDslGenerator,
    PropClassExtensionGenerator,
    AddFunctionsWrapperGenerator
)

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "application argument is required" }
    main(File(if (args.size >= 2) args[1] else "generated"), args[0])
}

fun main(targetDir: File, moduleName: String) {
    val cdkClasses = ClassPath.from(ClassLoader.getSystemClassLoader()).allClasses.asSequence()
        .filter { it.packageName.startsWith("software.amazon.awscdk") }
        .map { it.load() }
        .filter { File(it.protectionDomain.codeSource.location.toURI()).name.split('-').let { str -> str[str.lastIndex - 1] } == moduleName }

    val srcDir = File(targetDir, "src/main/kotlin").also { if (!it.exists()) it.mkdirs() }
    generators.forEach {
        it.run(cdkClasses, srcDir, moduleName)
    }
}

fun getFileSpecBuilder(fileName: String, packageName: String): FileSpec.Builder =
    FileSpec.builder("jp.justincase.cdkdsl.$packageName", fileName).apply {
        addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "FunctionName, Unused").build())
    }

fun Class<*>.getTrimmedPackageName() =
    `package`.name.split('.').drop(3).joinToString(".")