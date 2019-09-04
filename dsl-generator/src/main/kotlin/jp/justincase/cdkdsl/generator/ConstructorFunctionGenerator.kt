package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import software.amazon.awscdk.core.Construct
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter

object ConstructorFunctionGenerator : ICdkDslGenerator {
    private lateinit var file: FileSpec.Builder

    override fun run(classes: Sequence<Class<out Any>>, targetDir: File, moduleName: String) {
        file = getFileSpecBuilder("ConstructorFunctions", classes.firstOrNull()?.getTrimmedPackageName() ?: return)
        /*
        Generation target:
        ・have specific constructor parameter
        ・is not annotation, enum
         */
        classes
            .filter { !it.isAnnotation && !it.isEnum && !it.isAnonymousClass }
            .forEach { clazz ->
                val generator = when {
                    (clazz.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE)) != 0 -> InternalGenerator.Interface
                    clazz.constructors.singleOrNull { it.isResourceTypeConstructor() } != null -> InternalGenerator.WithId
                    clazz.constructors.singleOrNull { it.isPropertyOnlyConstructor() } != null -> InternalGenerator.OnlyProperty
                    else -> null
                }
                generator?.generate(clazz)
            }
        file.build().writeTo(targetDir)
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
        abstract fun generate(clazz: Class<*>)

        fun getPropClass(constructor: Constructor<*>) = constructor.parameters.single(::isPropertyArg).type!!

        fun getBuilderClassName(
            clazz: Class<*>,
            propClass: Class<*>
        ): ClassName {
            return if (propClass.declaringClass != null) {
                ClassName(
                    "jp.justincase.cdkdsl.${clazz.getTrimmedPackageName()}",
                    "jp.justincase.cdkdsl.${propClass.getTrimmedPackageName()}.${propClass.declaringClass.simpleName}.${propClass.simpleName}BuilderScope"
                )
            } else {
                ClassName(
                    "jp.justincase.cdkdsl.${clazz.getTrimmedPackageName()}",
                    "${propClass.simpleName}BuilderScope"
                )
            }
        }

        object WithId : InternalGenerator() {
            override fun generate(clazz: Class<*>) {
                clazz.constructors.singleOrNull { it.isResourceTypeConstructor() }?.let { constructor ->
                    val propClass = getPropClass(constructor)
                    val builderClass = getBuilderClassName(clazz, propClass)
                    file.addFunction(
                        FunSpec.builder(clazz.simpleName).apply {
                            returns(clazz)
                            receiver(Construct::class)
                            addParameter("id", String::class)
                            addParameter(
                                "configureProps",
                                LambdaTypeName.get(receiver = builderClass, returnType = UNIT)
                            )
                            addStatement("return %T(this, id, %T().also(configureProps).build())", clazz, builderClass)
                        }.build()
                    )
                }
            }
        }

        object OnlyProperty : InternalGenerator() {

            override fun generate(clazz: Class<*>) {
                clazz.constructors.singleOrNull { it.isPropertyOnlyConstructor() }?.let { constructor ->
                    val propClass = getPropClass(constructor)
                    val builderClass = getBuilderClassName(clazz, propClass)
                    file.addFunction(
                        FunSpec.builder(clazz.simpleName).apply {
                            returns(clazz)
                            receiver(Construct::class)
                            addParameter(
                                "configureProps",
                                LambdaTypeName.get(receiver = builderClass, returnType = UNIT)
                            )
                            addStatement("return %T(%T().also(configureProps).build())", clazz, builderClass)
                        }.build()
                    )
                }
            }
        }

        object Interface : InternalGenerator() {

            private fun Class<*>.haveBuilderClass() = declaredClasses.any { it.simpleName == "Builder" }

            override fun generate(clazz: Class<*>) {
                if (!clazz.haveBuilderClass()) return
                val builderClass = getBuilderClassName(clazz, clazz)

                file.addFunction(
                    FunSpec.builder(clazz.simpleName).apply {
                        returns(clazz)
                        receiver(Construct::class)
                        addParameter(
                            "configureProps",
                            LambdaTypeName.get(receiver = builderClass, returnType = UNIT)
                        )
                        addStatement("return %T().also(configureProps).build()", builderClass)
                    }.build()
                )
            }
        }
    }
}