package jp.justincase.cdkdsl.generator

import com.google.common.reflect.ClassPath
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * You can implement your own generator.
 * Create object implements [ICdkDslGenerator], add it to this, then run [main].
 */
val generators = mutableListOf(
    CoreDslGenerator,
    PlusOperatorFunctionsWrapperGenerator
)

suspend fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "application argument is required" }
    main(File(if (args.size >= 2) args[1] else "generated"), args[0])
}

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun main(targetDir: File, moduleName: String) {
    val cdkClasses = ClassPath.from(ClassLoader.getSystemClassLoader()).allClasses.asFlow()
        .filter { it.packageName.startsWith("software.amazon.awscdk") }
        .map { it.load() }
        .filter { File(it.protectionDomain.codeSource.location.toURI()).name.split('-').dropLast(1).joinToString("-") == moduleName }

    val srcDir = File(targetDir, "src/main/kotlin").also { if (!it.exists()) it.mkdirs() }

    val pack = cdkClasses.firstOrNull()?.getDslPackageName() ?: return
    generators.asFlow().collect {
        it.run(cdkClasses, srcDir, moduleName, pack)
    }
}

fun getFileSpecBuilder(fileName: String, packageName: String): FileSpec.Builder =
    FileSpec.builder(packageName, fileName).apply {
        addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "FunctionName, Unused").build())
    }

fun Class<*>.getTrimmedPackageName() =
    `package`.name.split('.').drop(3).joinToString(".")

fun Class<*>.getDslPackageName() = "jp.justincase.cdkdsl.${getTrimmedPackageName()}"

suspend fun <T> Flow<T>.firstOrNull(): T? = runCatching { first() }.getOrNull()