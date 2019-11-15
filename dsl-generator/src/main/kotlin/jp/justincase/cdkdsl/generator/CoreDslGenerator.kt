package jp.justincase.cdkdsl.generator

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import software.amazon.awscdk.core.Construct
import java.io.File
import java.lang.reflect.Modifier

object CoreDslGenerator : ICdkDslGenerator {
    override suspend fun run(classes: Flow<Class<out Any>>, targetDir: File, moduleName: String, packageName: String) {
        val filtered = classes.filter { clazz ->
            if (clazz.isInterface) {
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
                        it.name == "create" && it.parameterTypes.let { types ->
                            types[0] == Construct::class.java && types[1] == String::class.java
                        }
                    }
                } == true
            }
        }
        ConstructorFunctionGenerator.run(filtered, targetDir, packageName)
        PropClassExtensionGenerator.run(filtered, targetDir, packageName)
    }
}