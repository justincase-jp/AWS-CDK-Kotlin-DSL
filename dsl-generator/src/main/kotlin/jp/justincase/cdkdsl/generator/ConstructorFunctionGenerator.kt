package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import jp.justincase.cdkdsl.CdkDsl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import software.amazon.awscdk.core.Construct
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Parameter
import javax.annotation.Generated

object ConstructorFunctionGenerator {
    suspend fun run(
        classes: Flow<Pair<Class<out Any>, CoreDslGenerator.GenerationTarget>>,
        targetDir: File,
        packageName: String
    ) {
        val file = getFileSpecBuilder("ConstructorFunctions", packageName)

        classes
            .map { (clazz, target) ->
                when (target) {
                    CoreDslGenerator.GenerationTarget.INTERFACE -> InternalGenerator.Interface
                    CoreDslGenerator.GenerationTarget.RESOURCE -> InternalGenerator.WithId
                    CoreDslGenerator.GenerationTarget.NO_ID -> InternalGenerator.OnlyProperty
                }.generate(clazz)
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

        fun getBuilderScopeClassName(
            clazz: Class<*>
        ): ClassName {
            return if (clazz.declaringClass != null) {
                ClassName(
                    clazz.getDslPackageName(),
                    "${clazz.getDslPackageName()}.${clazz.declaringClass.simpleName}.${clazz.simpleName}BuilderScope"
                )
            } else {
                ClassName(
                    clazz.getDslPackageName(),
                    "${clazz.simpleName}BuilderScope"
                )
            }
        }

        object WithId : InternalGenerator() {
            override suspend fun generate(clazz: Class<*>): FunSpec? {
                val builderScopeClassName = getBuilderScopeClassName(clazz)
                return FunSpec.builder(clazz.simpleName).apply {
                    configureFun(clazz, builderScopeClassName)
                    addParameter("id", String::class)
                    addStatement(
                        "return %T(this, id).also(configureProps).build()",
                        builderScopeClassName
                    )
                }.build()
            }
        }

        object OnlyProperty : InternalGenerator() {

            override suspend fun generate(clazz: Class<*>): FunSpec? {
                val builderScopeClassName = getBuilderScopeClassName(clazz)
                return FunSpec.builder(clazz.simpleName).apply {
                    configureFun(clazz, builderScopeClassName)
                    addStatement("return %T().also(configureProps).build()", builderScopeClassName)
                }.build()
            }
        }

        object Interface : InternalGenerator() {

            private fun Class<*>.haveBuilderClass() = declaredClasses.any { it.simpleName == "Builder" }

            override suspend fun generate(clazz: Class<*>): FunSpec? {
                if (!clazz.haveBuilderClass()) return null
                val builderScopeClassName = getBuilderScopeClassName(clazz)
                return FunSpec.builder(clazz.simpleName).apply {
                    configureFun(clazz, builderScopeClassName)
                    addStatement("return %T().also(configureProps).build()", builderScopeClassName)
                }.build()
            }
        }
    }

    private fun FunSpec.Builder.configureFun(
        clazz: Class<*>,
        builderClass: ClassName
    ) {
        addAnnotation(CdkDsl::class)
        addAnnotation(AnnotationSpec.builder(Generated::class).apply {
            addMember("value = [\"jp.justincase.cdkdsl.generator.ConstructorFunctionGenerator\", \"justincase-jp/AWS-CDK-Kotlin-DSL\"]")
            addMember("date = \"$generationDate\"")
        }.build())
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