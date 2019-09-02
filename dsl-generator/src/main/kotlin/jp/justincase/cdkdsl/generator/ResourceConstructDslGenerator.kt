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

object ResourceConstructDslGenerator : ICdkDslGenerator {
    private lateinit var file: FileSpec.Builder

    override fun run(classes: Sequence<Class<out Any>>, targetDir: File, moduleName: String) {
        file = getFileSpecBuilder("ResourceConstructDsl", classes.firstOrNull()?.getTrimmedPackageName() ?: return)
        /*
        Generation target:
        ・subClass of software.amazon.awscdk.core.Resource or implement IResource
        ・have specific constructor parameter
        ・is not interface, annotation, abstract-class, enum
         */
        classes
            .filter { !it.isInterface && !it.isAnnotation && !it.isEnum && (it.modifiers and Modifier.ABSTRACT) == 0 }
            .forEach {
                @Suppress("UNCHECKED_CAST")
                if (isResourceSubClass(it)) {
                    InternalGenerator.WithId
                } else {
                    InternalGenerator.OnlyProperty
                }.generate(it as Class<out Resource>)
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

    private fun isPropertyArg(parameter: Parameter): Boolean =
        parameter.type != Construct::class.java
                && parameter.type != java.lang.String::class.java
                && !parameter.type.isPrimitive
                && parameter.type.declaredClasses.any { it.simpleName == "Builder" }

    @Suppress("unused")
    private sealed class InternalGenerator {
        abstract fun generate(clazz: Class<out Resource>)

        fun getPropClass(constructor: Constructor<*>) = constructor.parameters.single(::isPropertyArg).type!!

        fun getBuilderClassName(
            clazz: Class<out Resource>,
            propClass: Class<*>
        ): ClassName {
            return ClassName(
                "jp.justincase.cdkdsl.${clazz.getTrimmedPackageName()}",
                "${propClass.simpleName}BuilderScope"
            )
        }

        object WithId : InternalGenerator() {
            private fun checkConstructorIsValid(constructor: Constructor<*>) =
                constructor.parameters.size == 3
                        && constructor.parameters[0].type == Construct::class.java
                        && constructor.parameters[1].type == java.lang.String::class.java
                        && isPropertyArg(constructor.parameters[2])

            override fun generate(clazz: Class<out Resource>) {
                clazz.constructors.singleOrNull(this::checkConstructorIsValid)?.let { constructor ->
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
            private fun checkConstructorIsValid(constructor: Constructor<*>) =
                constructor.parameters.size == 1
                        && isPropertyArg(constructor.parameters[0])

            override fun generate(clazz: Class<out Resource>) {
                clazz.constructors.singleOrNull(this::checkConstructorIsValid)?.let { constructor ->
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
    }
}