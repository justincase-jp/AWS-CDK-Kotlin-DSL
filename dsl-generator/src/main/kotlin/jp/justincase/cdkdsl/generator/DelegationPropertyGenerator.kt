package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import software.amazon.awscdk.core.CfnResource
import software.amazon.awscdk.core.Construct
import software.amazon.awscdk.core.IResource
import software.amazon.awscdk.core.Resource
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Parameter
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaConstructor

object DelegationPropertyGenerator : ICdkDslGenerator {
    private lateinit var file: FileSpec.Builder

    override fun run(classes: Sequence<Class<out Any>>, targetDir: File, moduleName: String) {
        file = getFileSpecBuilder("DelegatedProperty", classes.first().getTrimmedPackageName())

        classes
            .filter { !it.isAnnotation && !it.isEnum && !it.isAnonymousClass }
            .filter { clazz -> clazz.isResourceSubClass() && clazz.constructors.singleOrNull { it.isResourceTypeConstructor() } != null }
            .filter { it.declaringClass == null }
            .map { it.kotlin }
            .forEach { generate(it) }

        file.build().writeTo(targetDir)
    }

    private fun generate(kclass: KClass<*>) {
        val constructor = kclass.constructors.single { it.javaConstructor!!.isResourceTypeConstructor() }
        val parameterClass = constructor.javaConstructor!!.parameters.single { it.isPropertyArg() }.type
        val delegateTypeName = "${kclass.simpleName}DelegatedProperty"
        val delegateTypeClassName = ClassName(kclass.java.getDslPackageName(), delegateTypeName)
        val delegateType = TypeSpec.classBuilder(delegateTypeName)
        delegateType.apply {
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("scope", Construct::class)
                    .addParameter("props", parameterClass)
                    .build()
            )
            addProperty(
                PropertySpec.builder("scope", Construct::class, KModifier.PRIVATE)
                    .initializer("scope")
                    .build()
            )
            addProperty(
                PropertySpec.builder("props", parameterClass, KModifier.PRIVATE)
                    .initializer("props")
                    .build()
            )
            addFunction(
                FunSpec.builder("getValue")
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", Any::class.asTypeName().copy(nullable = true))
                    .addParameter(
                        "property",
                        ParameterizedTypeName.run { KProperty::class.asTypeName().plusParameter(STAR) })
                    .returns(kclass)
                    .addStatement("return %T(scope, property.name, props)", kclass)
                    .build()
            )
        }
        val builderClassName = getBuilderClassName(kclass.java, parameterClass)
        val builderFunc = FunSpec.builder(kclass.simpleName!!)
            .receiver(Construct::class)
            .returns(delegateTypeClassName)
            .addParameter(
                "configureProps",
                LambdaTypeName.get(
                    receiver = builderClassName,
                    returnType = UNIT
                )
            )
            .addStatement("return $delegateTypeName(this, $builderClassName().also(configureProps).build())")
        file.addType(delegateType.build())
        file.addFunction(builderFunc.build())
    }

    private tailrec fun Class<*>.isResourceSubClass(): Boolean {
        if (interfaces.contains(IResource::class.java)) {
            return true
        } else if (superclass == Object::class.java || superclass == null) {
            return false
        } else if (superclass == Resource::class.java || superclass == CfnResource::class.java) {
            return true
        }
        return superclass.isResourceSubClass()
    }

    private fun Constructor<*>.isResourceTypeConstructor() =
        parameters.size == 3
                && parameters[0].type == Construct::class.java
                && parameters[1].type == java.lang.String::class.java
                && parameters[2].isPropertyArg()

    private fun Parameter.isPropertyArg(): Boolean =
        type != Construct::class.java
                && type != java.lang.String::class.java
                && !type.isPrimitive
                && type.declaredClasses.any { it.simpleName == "Builder" }

    private fun getBuilderClassName(
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
}