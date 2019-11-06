package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import jp.justincase.cdkdsl.CdkDsl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import software.amazon.awscdk.core.Construct
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import javax.annotation.Generated

object ConstructorFunctionGenerator : ICdkDslGenerator {
    override suspend fun run(classes: Flow<Class<out Any>>, targetDir: File, moduleName: String, packageName: String) {
        val file = getFileSpecBuilder("ConstructorFunctions", packageName)
        /*
        Generation target:
        ・have specific constructor parameter
        ・is not annotation, enum
         */
        classes
            .filter { !it.isAnnotation && !it.isEnum && !it.isAnonymousClass }
            .map { clazz ->
                val generator = when {
                    (clazz.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE)) != 0 -> InternalGenerator.Interface
                    clazz.constructors.singleOrNull { it.isResourceTypeConstructor() } != null -> InternalGenerator.WithId
                    clazz.constructors.singleOrNull { it.isPropertyOnlyConstructor() } != null -> InternalGenerator.OnlyProperty
                    else -> null
                }
                generator?.generate(clazz)
            }.collect {
                it?.apply { file.addFunction(this) }
            }
        withContext(Dispatchers.IO) {
            file.build().writeTo(targetDir)
        }
    }

    private fun isPropertyArg(parameter: Parameter): Boolean =
        parameter.type != Construct::class.java
                && parameter.type != java.lang.String::class.java
                && !parameter.type.isPrimitive
                && parameter.type.declaredClasses.any { it.simpleName == "Builder" }

    private fun Constructor<*>.isResourceTypeConstructor() =
        parameters.size == 3
                && parameters[0].type == Construct::class.java
                && parameters[1].type == java.lang.String::class.java
                && isPropertyArg(parameters[2])

    private fun Constructor<*>.isPropertyOnlyConstructor() =
        parameters.size == 1
                && isPropertyArg(parameters[0])

    @Suppress("unused")
    private sealed class InternalGenerator {
        abstract suspend fun generate(clazz: Class<*>): FunSpec?

        fun getPropClass(constructor: Constructor<*>) = constructor.parameters.single(::isPropertyArg).type!!

        fun getBuilderClassName(
            clazz: Class<*>,
            propClass: Class<*>
        ): ClassName {
            return if (propClass.declaringClass != null) {
                ClassName(
                    clazz.getDslPackageName(),
                    "${propClass.getDslPackageName()}.${propClass.declaringClass.simpleName}.${propClass.simpleName}BuilderScope"
                )
            } else {
                ClassName(
                    clazz.getDslPackageName(),
                    "${propClass.simpleName}BuilderScope"
                )
            }
        }

        object WithId : InternalGenerator() {
            override suspend fun generate(clazz: Class<*>) = clazz.constructors
                .singleOrNull { it.isResourceTypeConstructor() }
                ?.let { constructor ->
                    val propClass = getPropClass(constructor)
                    val builderClass = getBuilderClassName(clazz, propClass)
                    FunSpec.builder(clazz.simpleName).apply {
                        configureFun(clazz, builderClass)
                        addParameter("id", String::class)
                        addStatement("return %T(this, id, %T().also(configureProps).build())", clazz, builderClass)
                    }.build()
                }
        }

        object OnlyProperty : InternalGenerator() {

            override suspend fun generate(clazz: Class<*>) = clazz.constructors
                .singleOrNull { it.isPropertyOnlyConstructor() }
                ?.let { constructor ->
                    val propClass = getPropClass(constructor)
                    val builderClass = getBuilderClassName(clazz, propClass)
                    FunSpec.builder(clazz.simpleName).apply {
                        configureFun(clazz, builderClass)
                        addStatement("return %T(%T().also(configureProps).build())", clazz, builderClass)
                    }.build()
                }
        }

        object Interface : InternalGenerator() {

            private fun Class<*>.haveBuilderClass() = declaredClasses.any { it.simpleName == "Builder" }

            override suspend fun generate(clazz: Class<*>): FunSpec? {
                if (!clazz.haveBuilderClass()) return null
                val builderClass = getBuilderClassName(clazz, clazz)
                return FunSpec.builder(clazz.simpleName).apply {
                    configureFun(clazz, builderClass)
                    addStatement("return %T().also(configureProps).build()", builderClass)
                }.build()
            }
        }
    }

    private fun FunSpec.Builder.configureFun(
        clazz: Class<*>,
        builderClass: ClassName
    ) {
        addAnnotation(CdkDsl::class)
        addAnnotation(Generated::class)
        returns(clazz)
        receiver(Construct::class)
        addParameter(
            ParameterSpec.builder(
                "configureProps",
                LambdaTypeName.get(receiver = builderClass, returnType = UNIT)
            ).defaultValue("{ }").build()
        )
    }
}