package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredFunctions

object PlusOperatorFunctionsWrapperGenerator : ICdkDslGenerator {
    private lateinit var propBuilderFile: FileSpec.Builder
    private lateinit var operatorFunFile: FileSpec.Builder
    private lateinit var packageName: String

    private val wrappedPropClasses = mutableSetOf<KClass<*>>()

    override fun run(classes: Sequence<Class<out Any>>, targetDir: File, moduleName: String) {
        packageName = classes.first().getTrimmedPackageName()
        propBuilderFile = getFileSpecBuilder("PropBuilder", packageName)
        operatorFunFile = getFileSpecBuilder("PlusAssignOperators", packageName)

        val propClasses = classes.filter { it.simpleName == "Builder" }.map { it.declaringClass.kotlin }.toList()
        classes.toList()
            .filterNot {
                it.simpleName.contains("Jsii") || it.simpleName.contains("Builder") || propClasses.contains(it.kotlin) || it.isAnonymousClass
            }.map { it.kotlin }.associateWith { clazz ->
                clazz.declaredFunctions.filter { method ->
                    !method.isExternal &&
                            method.name.startsWith("add") &&
                            method.parameters
                                .filter { it.kind == KParameter.Kind.VALUE }.run {
                                    size == 2 && map { it.type }.let { parameterClasses ->
                                        parameterClasses[0].classifier == String::class && propClasses.contains(
                                            parameterClasses[1].classifier as? KClass<out Any>
                                        )
                                    }
                                }
                }
            }.filterValues { it.isNotEmpty() }.forEach { (clazz, functions) ->
                functions.forEach { func ->
                    val propClass =
                        func.parameters.filter { it.kind == KParameter.Kind.VALUE }[1].type.classifier as KClass<*>

                    val builderScope = ClassName(
                        "jp.justincase.cdkdsl.${propClass.java.getTrimmedPackageName()}",
                        "${propClass.simpleName}BuilderScope"
                    )
                    val lambdaType = LambdaTypeName.get(
                        builderScope, returnType = UNIT
                    )

                    if (!wrappedPropClasses.contains(propClass)) {
                        createPropBuilder(propClass, lambdaType)
                    }
                    createPlusAssign(clazz, lambdaType, builderScope, func)
                }
            }
        propBuilderFile.build().writeTo(targetDir)
        operatorFunFile.build().writeTo(targetDir)
    }

    private fun createPropBuilder(propClass: KClass<*>, lambdaType: LambdaTypeName) {
        propBuilderFile.addFunction(
            FunSpec.builder(propClass.simpleName!!.decapitalize())
                .addParameter("id", String::class)
                .addParameter(
                    "configuration",
                    lambdaType
                )
                .addStatement("return id.to(configuration)")
                .build()
        )
        wrappedPropClasses += propClass
    }

    private fun createPlusAssign(
        clazz: KClass<out Any>,
        lambdaType: LambdaTypeName,
        builderScope: ClassName,
        func: KFunction<*>
    ) {
        operatorFunFile.addFunction(
            FunSpec.builder("plusAssign")
                .addAnnotation(
                    AnnotationSpec.builder(JvmName::class).addMember(
                        "%S",
                        "plugAssign${builderScope.simpleName}"
                    ).build()
                )
                .addModifiers(KModifier.OPERATOR)
                .receiver(clazz)
                .returns(UNIT)
                .addParameter("value", ParameterizedTypeName.run {
                    Pair::class.asTypeName().parameterizedBy(
                        String::class.asTypeName(), lambdaType
                    )
                })
                .addStatement(
                    "${func.name}(value.first, %T().also(value.second).build())",
                    builderScope
                )
                .build()
        )
    }
}