package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import software.amazon.awscdk.core.Construct
import software.amazon.awscdk.core.IResource
import software.amazon.awscdk.core.Resource
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.util.*

private lateinit var file: FileSpec.Builder
val propClasses: Set<Class<*>>
    get() = propClazz.toSet()
private val propClazz = mutableSetOf<Class<*>>()

fun genResourceConstructResource(classes: Sequence<Class<out Any>>, targetDir: File, moduleName: String) {
    file = getFileSpecBuilder("ResourceConstructDsl", moduleName)
    /*
    Generation target:
    ・subClass of software.amazon.awscdk.core.Resource or implement IResource
    ・have specific constructor parameter
    ・is not interface, annotation, abstract-class, enum
     */
    classes
        .filter(::isResourceSubClass)
        .filter { !it.isInterface && !it.isAnnotation && !it.isEnum && (it.modifiers and Modifier.ABSTRACT) == 0 }
        .forEach {
            @Suppress("UNCHECKED_CAST")
            generate(it as Class<out Resource>)
        }
    file.build().writeTo(targetDir)
}

private tailrec fun isResourceSubClass(clazz: Class<*>): Boolean {
    if (clazz.interfaces.contains(IResource::class.java)) {
        return true
    } else if (clazz.superclass == Objects::class.java || clazz.superclass == null) {
        return false
    } else if (clazz.superclass == Resource::class.java) {
        return true
    }
    return isResourceSubClass(clazz.superclass)
}

private fun checkConstructorIsValid(constructor: Constructor<*>) =
    constructor.parameters.size == 3
            && constructor.parameters[0].type == Construct::class.java
            && constructor.parameters[1].type == java.lang.String::class.java
            && isPropertyArg(constructor.parameters[2])

private fun generate(clazz: Class<out Resource>) {
    clazz.constructors.singleOrNull(::checkConstructorIsValid)?.let { constructor ->
        val propClass = constructor.parameters.single(::isPropertyArg).type
        val builderClass = propClass.declaredClasses.single { it.simpleName == "Builder" }
        propClazz += propClass
        file.addFunction(
            FunSpec.builder(clazz.simpleName).apply {
                returns(clazz)
                addParameter("scope", Construct::class)
                addParameter("id", String::class)
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
            && !parameter.type.isPrimitive
            && parameter.type.declaredClasses.any { it.simpleName == "Builder" }
