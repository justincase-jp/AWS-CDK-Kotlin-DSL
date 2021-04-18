package jp.justincase.cdkdsl.generator

import jp.justincase.cdkdsl.generator.utility.superTypesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import software.constructs.IConstruct
import java.io.File
import java.lang.reflect.Modifier

object CoreDslGenerator : ICdkDslGenerator {

    private val createArgType = listOf(IConstruct::class.java, java.lang.String::class.java)

    /*
      フィルター条件(インターフェースの場合):
      1. Builderクラスを持っていること
      2. Builderクラスのコンストラクター(引数なし)がpublicなこと
      フィルター条件(クラスの場合)：
      1. Builderクラスを持っていること
      2. Builderクラスのcreate()関数が特定の引数を持っていること
    */
    override suspend fun run(classes: Flow<Class<out Any>>, targetDir: File, moduleName: String, packageName: String) =
        classes.mapNotNull { clazz -> clazz.declaredClasses.singleOrNull { it.simpleName == "Builder" }?.to(clazz) }
            .filter { (builder, base) ->
                if (base.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE) != 0) {
                    builder.constructors.any { constructor ->
                        (constructor.modifiers and Modifier.PUBLIC) != 0 && constructor.parameterCount == 0
                    }
                } else {
                    builder.declaredMethods.any {
                        it.name == "create" && it.parameterCount == 0 || createArgType.superTypesOf(it.parameterTypes)
                    }
                }
            }.mapNotNull { (builder, clazz) ->
                clazz to when {
                    (clazz.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE)) != 0 -> GenerationTarget.INTERFACE
                    builder.isResourceTypeBuilder() -> GenerationTarget.RESOURCE
                    builder.isPropertyOnlyTypeBuilder() -> GenerationTarget.NO_ID
                    else -> return@mapNotNull null
                }
            }.let { flow ->
                PropClassExtensionGenerator.run(flow, targetDir, packageName)
                ConstructorFunctionGenerator.run(flow, targetDir, packageName)
            }

    private fun Class<*>.isResourceTypeBuilder() =
        declaredMethods.singleOrNull { it.name == "create" }?.let {
            createArgType.superTypesOf(it.parameterTypes)
        } ?: false

    private fun Class<*>.isPropertyOnlyTypeBuilder() =
        declaredMethods.singleOrNull { it.name == "create" }?.parameterCount == 0

    enum class GenerationTarget {
        // Interface, abstract class
        INTERFACE,

        // scope: Construct, id: Stringをcreate()の引数に持つもの
        RESOURCE,

        // create()の引数が空のもの
        NO_ID
    }
}