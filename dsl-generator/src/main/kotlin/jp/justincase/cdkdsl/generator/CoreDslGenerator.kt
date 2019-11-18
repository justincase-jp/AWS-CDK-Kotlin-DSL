package jp.justincase.cdkdsl.generator

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import software.amazon.awscdk.core.Construct
import java.io.File
import java.lang.reflect.Modifier

object CoreDslGenerator : ICdkDslGenerator {

    override suspend fun run(classes: Flow<Class<out Any>>, targetDir: File, moduleName: String, packageName: String) {
        val filtered = classes.filter { clazz ->
            if (clazz.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE) != 0) {
                /*
                フィルター条件(インターフェースの場合):
                1. Builderクラスを持っていること
                2. Builderクラスのコンストラクター(引数なし)がpublicなこと
                 */
                clazz.declaredClasses.singleOrNull { it.name == "Builder" }?.let { builder ->
                    builder.constructors.any { constructor ->
                        (constructor.modifiers and Modifier.PUBLIC) != 0 && constructor.parameterCount == 0
                    }
                } == true
            } else {
                /*
                フィルター条件(クラスの場合)：
                1. Builderクラスを持っていること
                2. Builderクラスのcreate()関数が特定の引数を持っていること
                */
                clazz.declaredClasses.singleOrNull { it.name == "Builder" }?.let { builder ->
                    builder.declaredMethods.any {
                        it.name == "create" &&
                            it.parameterCount == 0 || (
                            it.parameterCount == 2 &&
                                it.parameterTypes[0] == Construct::class.java &&
                                it.parameterTypes[1] == java.lang.String::class.java
                            )
                    }
                } == true
            }
        }
        filtered.mapNotNull { clazz ->
            val builder = clazz.declaredClasses.single { it.name == "Builder" }
            clazz to when {
                (clazz.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE)) != 0 -> GenerationTarget.INTERFACE
                builder.isResourceTypeBuilder() -> GenerationTarget.RESOURCE
                builder.isPropertyOnlyTypeBuilder() -> GenerationTarget.NO_ID
                else -> return@mapNotNull null
            }
        }.collect {
            // ToDo
        }
    }

    private fun Class<*>.isResourceTypeBuilder() =
        declaredMethods.single { it.name == "create" }.let {
            it.parameterCount == 2 &&
                it.parameterTypes[0] == Construct::class.java &&
                it.parameterTypes[1] == java.lang.String::class.java
        }

    private fun Class<*>.isPropertyOnlyTypeBuilder() =
        declaredMethods.single { it.name == "create" }.parameterCount == 0

    enum class GenerationTarget {
        // Interface, abstract class
        INTERFACE,
        // scope: Construct, id: Stringをcreate()の引数に持つもの
        RESOURCE,
        // create()の引数が空のもの
        NO_ID
    }
}