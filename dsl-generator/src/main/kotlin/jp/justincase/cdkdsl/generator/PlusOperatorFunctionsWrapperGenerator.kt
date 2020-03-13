package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import jp.justincase.cdkdsl.CdkDsl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import javax.annotation.Generated
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredFunctions

object PlusOperatorFunctionsWrapperGenerator : ICdkDslGenerator {

    @OptIn(FlowPreview::class)
    override suspend fun run(classes: Flow<Class<out Any>>, targetDir: File, moduleName: String, packageName: String) {
        val propBuilderFile = getFileSpecBuilder("PropBuilder", packageName)
        val operatorFunFile = getFileSpecBuilder("PlusAssignOperators", packageName)

        val propClasses = classes.filter { it.simpleName == "Builder" }.map { it.declaringClass.kotlin }.toList()
        val wrappedPropClasses = mutableSetOf<KClass<*>>()
        classes
            .filterNot {
                it.simpleName.contains("Jsii") || it.simpleName.contains("Builder") || propClasses.contains(it.kotlin) || it.isAnonymousClass
            }.map { it.kotlin }.map { clazz ->
                clazz to clazz.declaredFunctions.filter { method ->
                    !method.isExternal && method.name.startsWith("add") && method.parameters.filter {
                        it.kind == KParameter.Kind.VALUE
                    }.run {
                        size == 2 && map { it.type.classifier }.let { classifiers ->
                            classifiers[0] == String::class && propClasses.contains(classifiers[1] as? KClass<out Any>)
                        }
                    }
                }
            }.filter { it.second.isNotEmpty() }.flatMapConcat { (clazz, functions) ->
                functions.asFlow().map { func ->
                    val propClass =
                        func.parameters.filter { it.kind == KParameter.Kind.VALUE }[1].type.classifier as KClass<*>

                    val builderScope = ClassName(
                        propClass.java.getDslPackageName(),
                        "${propClass.simpleName}BuilderScope"
                    )
                    val lambdaType = LambdaTypeName.get(
                        builderScope, returnType = UNIT
                    )

                    (if (!wrappedPropClasses.contains(propClass)) {
                        createPropBuilder(propClass, lambdaType).apply { wrappedPropClasses.add(propClass) }
                    } else null) to createPlusAssign(clazz, lambdaType, builderScope, func)
                }
            }.collect { (prop, op) ->
                prop?.apply { propBuilderFile.addFunction(this) }
                operatorFunFile.addFunction(op)
            }
        withContext(Dispatchers.IO) {
            propBuilderFile.build().writeTo(targetDir)
            operatorFunFile.build().writeTo(targetDir)
        }
    }

    private fun createPropBuilder(
        propClass: KClass<*>,
        lambdaType: LambdaTypeName
    ): FunSpec = FunSpec.builder(propClass.simpleName!!.decapitalize()).apply {
        addAnnotation(CdkDsl::class)
        addAnnotation(AnnotationSpec.builder(Generated::class).apply {
            addMember("value = [\"jp.justincase.cdkdsl.generator.PlusOperatorFunctionsWrapperGenerator\", \"justincase-jp/AWS-CDK-Kotlin-DSL\"]")
            addMember("date = \"$generationDate\"")
        }.build())
        addParameter("id", String::class)
        addParameter(
            "configuration",
            lambdaType
        )
        addStatement("return id.to(configuration)")
    }.build()

    private fun createPlusAssign(
        clazz: KClass<out Any>,
        lambdaType: LambdaTypeName,
        builderScope: ClassName,
        func: KFunction<*>
    ): FunSpec = FunSpec.builder("plusAssign").apply {
        addAnnotation(CdkDsl::class)
        addAnnotation(AnnotationSpec.builder(Generated::class).apply {
            addMember("value = [\"jp.justincase.cdkdsl.generator.PlusOperatorFunctionsWrapperGenerator\", \"justincase-jp/AWS-CDK-Kotlin-DSL\"]")
            addMember("date = \"$generationDate\"")
        }.build())
        addAnnotation(
            AnnotationSpec.builder(JvmName::class).addMember(
                "%S",
                "plugAssign${builderScope.simpleName}"
            ).build()
        )
        addModifiers(KModifier.OPERATOR)
        receiver(clazz)
        returns(UNIT)
        addParameter("value", ParameterizedTypeName.run {
            Pair::class.asTypeName().parameterizedBy(
                String::class.asTypeName(), lambdaType
            )
        })
        addStatement(
            "${func.name}(value.first, %T().also(value.second).build())",
            builderScope
        )
    }.build()
}