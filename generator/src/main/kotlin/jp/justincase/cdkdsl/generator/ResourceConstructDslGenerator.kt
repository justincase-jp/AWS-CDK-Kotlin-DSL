package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import software.amazon.awscdk.core.Construct
import software.amazon.awscdk.core.Resource
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.util.*

val file = FileSpec.builder("jp.justincase.cdkdsl", "ResourceConstructDsl")

fun genResourceConstructResource(classes: Sequence<Class<out Any>>) {
    file.addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "FunctionName").build())
    classes
        .filter(::isResourceSubClass)
        .filter(::checkConstructorHasPropertyArgument)
        .filter { !it.isInterface && !it.isAnnotation && !it.isEnum && (it.modifiers and Modifier.ABSTRACT) == 0 }
        .forEach {
            @Suppress("UNCHECKED_CAST")
            generate(it as Class<out Resource>)
        }
    file.build().writeTo(File("generated/src/main/kotlin").also { if (!it.exists()) it.mkdirs() })
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
    val constructor = clazz.constructors.single { it.parameters.any(::isPropertyArg) }
    val propClass = constructor.parameters.single(::isPropertyArg).type
    val builderClass = propClass.declaredClasses.singleOrNull { it.simpleName == "Builder" } ?: return
    file.addFunction(
        FunSpec.builder(clazz.simpleName)
            .returns(clazz)
            .addParameter("scope", Construct::class)
            .addParameter("id", String::class)
            .addParameter(
                ParameterSpec.builder(
                    "configureProps",
                    LambdaTypeName.get(receiver = builderClass.asTypeName(), returnType = UNIT)
                ).build()
            )
            .addStatement("return %T(scope, id, %T.builder().also(configureProps).build())", clazz, propClass)
            .build()
    )
}

private fun isPropertyArg(parameter: Parameter): Boolean =
    parameter.type != Construct::class.java && parameter.type != java.lang.String::class.java
