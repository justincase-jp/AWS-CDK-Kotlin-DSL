package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import software.amazon.awscdk.core.IResolvable
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberFunctions

object PropClassExtensionGenerator : ICdkDslGenerator {
    private lateinit var file: FileSpec.Builder
    private val classFiles = mutableMapOf<String, FileSpec.Builder>()

    private val ignoreFunctionNames = setOf("build", "toString", "hashCode", "equals")

    override fun run(classes: Sequence<Class<out Any>>, targetDir: File, moduleName: String) {
        file = getFileSpecBuilder("PropClassExtensions", classes.first().getTrimmedPackageName())

        val classGroup = classes.filter { it.simpleName == "Builder" }
            .map { it.kotlin }
            .groupBy { it.java.declaringClass.declaringClass == null }
        val parentMap = mutableMapOf<Class<*>, TypeSpec.Builder>()
        buildClasses(classGroup[true] ?: emptyList()).forEach { (clazz, spec) ->
            getClassFile(clazz).addType(spec)
        }
        buildClasses(classGroup[false] ?: emptyList()).forEach { (clazz, spec) ->
            val parentClass = clazz.java.declaringClass.declaringClass
            val parent = if (parentMap.containsKey(parentClass)) {
                parentMap.getValue(parentClass)
            } else {
                TypeSpec.classBuilder(parentClass.simpleName).apply {
                    parentMap[parentClass] = this
                }
            }
            parent.addType(spec)
        }
        parentMap.forEach { (clazz, builder) ->
            getClassFile(clazz.kotlin).addType(builder.build())
        }
        classFiles.forEach { (_, builder) ->
            builder.build().writeTo(targetDir)
        }
        file.build().writeTo(targetDir)
    }

    private fun buildClasses(list: List<KClass<*>>) = list.associateWith { clazz ->
        val wrapper = TypeSpec.classBuilder("${clazz.java.declaringClass.simpleName}BuilderScope")
        val methods = clazz.memberFunctions
            .filter { !ignoreFunctionNames.contains(it.name) && it.arguments.size == 1 && !it.isExternal }
        val duplicates = methods.groupBy { it.name }.filterValues { it.count() >= 2 }.toMutableMap()
        val handledDuplicates = mutableSetOf<String>()
        if (methods.map { it.name }.toSet().size != methods.size) {
            if (!methods.any { it.arguments.map { param -> param.type }.single() == IResolvable::class }) {
                return@associateWith null
            }
        }
        methods.forEach {
            val name = it.name
            val dup = duplicates[name]
            if (dup != null) {
                if (!handledDuplicates.contains(name)) {
                    wrapper.addPropertyForDuplicatedMethods(name, dup)
                    handledDuplicates += name
                }
            } else {
                wrapper.addProperty(it)
            }
        }
        handledDuplicates.clear()
        wrapper.addFunction(FunSpec.builder("build")
            .returns(clazz.java.declaringClass.kotlin)
            .addStatement("val builder = %T()", clazz)
            .apply {
                methods.forEach { method ->
                    val name = method.name
                    val fieldName = name.decapitalize()
                    if (duplicates[name] != null) {
                        addStatement("if( _$fieldName != null && ${fieldName}TypeFlag != null) {")
                        addStatement(
                            "if( ${fieldName}TypeFlag ) { builder.$name( _$fieldName as IResolvable) }"
                        )
                        addStatement(
                            "else { builder.$name( _$name as %T }",
                            duplicates[name]!!.single { it.arguments.single().type != IResolvable::class }.arguments.single().type
                        )
                        addStatement("}")
                    } else {
                        addStatement("${fieldName}?.let{ builder.$name(it) }")
                    }
                }
            }
            .addStatement("return builder.build()")
            .build())
        wrapper.build()
    }.mapNotNull { if (it.value == null) null else it.key to it.value!! }.toMap()

    private fun TypeSpec.Builder.addProperty(method: KFunction<*>) {
        val parameterType = method.arguments.single().type.asTypeName().copy(nullable = true)
        addProperty(
            PropertySpec.builder(method.name.decapitalize(), parameterType)
                .mutable()
                .initializer("null")
                .build()
        )
    }

    private fun TypeSpec.Builder.addPropertyForDuplicatedMethods(name: String, methods: List<KFunction<*>>) {
        val fieldName = name.decapitalize()
        addProperty(
            PropertySpec.builder(
                "_$fieldName",
                Any::class.asTypeName().copy(nullable = true),
                KModifier.PRIVATE
            ).initializer("null").mutable().build()
        )
        addProperty(
            PropertySpec.builder(
                "${fieldName}TypeFlag",
                Boolean::class.asTypeName().copy(nullable = true),
                KModifier.PRIVATE
            ).initializer("false").mutable().build()
        )
        methods.forEach {
            val parameterType = it.arguments.single().type
            val nullable = parameterType.asTypeName().copy(nullable = true)
            addProperty(
                PropertySpec.builder(
                    if (parameterType == IResolvable::class.java) name else "cfn${name.capitalize()}",
                    nullable
                )
                    .mutable()
                    .setter(
                        FunSpec.setterBuilder()
                            .addParameter("value", nullable)
                            .addStatement("_$fieldName = value")
                            .addStatement("${fieldName}TypeFlag = true")
                            .build()
                    )
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("return _$fieldName as? %T", nullable)
                            .build()
                    ).build()
            )
        }
    }

    private fun getClassFile(clazz: KClass<*>): FileSpec.Builder {
        val pack = clazz.java.getTrimmedPackageName()
        return classFiles[pack] ?: FileSpec.builder(
            "jp.justincase.cdkdsl.$pack",
            pack.split('.').last().capitalize()
        ).apply {
            addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S, %S", "FunctionName", "Unused").build())
            classFiles[pack] = this
        }
    }

    val KFunction<*>.arguments
        get() = parameters.filter { it.kind == KParameter.Kind.VALUE }

}