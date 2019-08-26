package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import software.amazon.awscdk.core.Construct
import software.amazon.awscdk.core.Resource
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.util.*

val file = FileSpec.builder("jp.justincase.cdkdsl", "ResourceConstructDsl")

fun genResourceConstructResource(classes: Sequence<Class<out Any>>, targetDir: File) {
    file.addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "FunctionName, Unused").build())
    /*
    生成対象となるクラスの条件
    ・software.amazon.awscdk.core.Resourceのサブクラスであること
    ・いずれかのコンストラクターが"props"という名前の〇〇Props系のクラスを引数に持っていること
    ・インターフェース・アノテーション・Enum・抽象クラスでないこと
     */
    classes
        .filter(::isResourceSubClass)
        .filter(::checkConstructorHasPropertyArgument)
        .filter { !it.isInterface && !it.isAnnotation && !it.isEnum && (it.modifiers and Modifier.ABSTRACT) == 0 }
        .forEach {
            @Suppress("UNCHECKED_CAST")
            generate(it as Class<out Resource>)
        }
    file.build().writeTo(File(targetDir, "src/main/kotlin").also { if (!it.exists()) it.mkdirs() })
}

// 1行に合成可能だけどtailrecを付けたいのであえて分割
private tailrec fun isResourceSubClass(clazz: Class<*>): Boolean {
    if (clazz.superclass == Objects::class.java || clazz.superclass == null) {
        return false
    } else if (clazz.superclass == Resource::class.java) {
        return true
    }
    return isResourceSubClass(clazz.superclass)
}

private fun checkConstructorHasPropertyArgument(clazz: Class<*>) =
    clazz.constructors.any { constructor -> constructor.parameters.any(::isPropertyArg) }

private fun generate(clazz: Class<out Resource>) {
    clazz.constructors.filter { it.parameters.any(::isPropertyArg) }.forEach { constructor ->
        val propClass = constructor.parameters.single(::isPropertyArg).type
        val builderClass = propClass.declaredClasses.single { it.simpleName == "Builder" }
        file.addFunction(
            FunSpec.builder(clazz.simpleName.decapitalize()).apply {
                returns(clazz)
                constructor.parameters.filter { it.type != propClass }.forEach {
                    addParameter(it.name, it.type)
                }
                addParameter(
                    "configureProps",
                    LambdaTypeName.get(receiver = builderClass.asTypeName(), returnType = UNIT)
                )
                addStatement("return %T(scope, id, %T().also(configureProps).build())", builderClass, propClass)
            }.build()
        )
    }
}

private fun isPropertyArg(parameter: Parameter): Boolean =
    parameter.type != Construct::class.java
            && parameter.type != java.lang.String::class.java
            && parameter.name == "props"
            && parameter.type.declaredClasses.any { it.simpleName == "Builder" }
