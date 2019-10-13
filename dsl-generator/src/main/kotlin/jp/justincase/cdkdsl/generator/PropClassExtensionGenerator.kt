package jp.justincase.cdkdsl.generator

import com.squareup.kotlinpoet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberFunctions

object PropClassExtensionGenerator : ICdkDslGenerator {

    private val ignoreFunctionNames = setOf("build", "toString", "hashCode", "equals")

    override suspend fun run(classes: Flow<Class<out Any>>, targetDir: File, moduleName: String, packageName: String) {
        val file = getFileSpecBuilder(packageName.split('.').last().capitalize(), packageName)

        file.addAliasedImport(MemberName("kotlin.collections", "plus"), "nonNullPlus")

        val builderClasses = classes.filter { it.simpleName == "Builder" }
            .map { it.kotlin }

        buildClasses(builderClasses.filter { it.java.declaringClass.declaringClass == null })
            .forEach { (_, spec) ->
                file.addType(spec)
            }

        val parentMap = mutableMapOf<Class<*>, TypeSpec.Builder>()
        buildClasses(builderClasses.filter { it.java.declaringClass.declaringClass != null }).forEach { (clazz, spec) ->
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
        parentMap.forEach { (_, builder) ->
            file.addType(builder.build())
        }

        withContext(Dispatchers.IO) { file.build().writeTo(targetDir) }
    }

    private suspend fun buildClasses(flow: Flow<KClass<*>>): Map<KClass<*>, TypeSpec> {
        return flow.map { clazz ->
            val wrapper = TypeSpec.classBuilder("${clazz.java.declaringClass.simpleName}BuilderScope")
            wrapper.addModifiers(KModifier.OPEN)
            wrapper.addCommonFunctions()
            val methods = clazz.memberFunctions
                .filter { !ignoreFunctionNames.contains(it.name) && it.arguments.size == 1 && !it.isExternal }
            val duplicates = methods.groupBy { it.name }.filterValues { it.count() >= 2 }.toMutableMap()
            val handledDuplicates = mutableSetOf<String>()
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
            val funSpec = FunSpec.builder("build").apply {
                returns(clazz.java.declaringClass.kotlin)
                addStatement("val builder = %T()", clazz)
                methods.forEach { method ->
                    val name = method.name
                    val fieldName = name.decapitalize()
                    if (duplicates[name] != null) {
                        if (!handledDuplicates.contains(name)) {
                            beginControlFlow("when(val v = $fieldName)")
                            duplicates[name]!!.forEach { func ->
                                val propType = (func.parameters.single {
                                    it.kind == KParameter.Kind.VALUE
                                }.type.classifier as KClass<*>).simpleName
                                addStatement("is ${name.capitalize()}.$propType -> builder.$name(v.value)")
                            }
                            endControlFlow()
                            handledDuplicates += name
                        }
                    } else {
                        addStatement("${fieldName}?.let{ builder.$name(it) }")
                    }
                }
                addStatement("return builder.build()")
            }.build()
            wrapper.addFunction(funSpec)
            clazz to wrapper.build()
        }.toList().toMap()
    }

    private fun TypeSpec.Builder.addCommonFunctions() {
        val typeVariable = TypeVariableName("T")
        val mapTypeVariables = listOf(TypeVariableName("K"), TypeVariableName("V"))
        val nullableListType =
            ParameterizedTypeName.run { List::class.asTypeName().plusParameter(typeVariable) }.copy(nullable = true)
        val nullableMapType = ParameterizedTypeName
            .run { Map::class.asTypeName().parameterizedBy(*(mapTypeVariables.toTypedArray())) }
            .copy(nullable = true)
        val pairType = ParameterizedTypeName
            .run { Pair::class.asTypeName().parameterizedBy(*(mapTypeVariables.toTypedArray())) }
        addFunction(
            FunSpec.builder("plus").apply {
                returns(nullableListType)
                receiver(nullableListType)
                addModifiers(KModifier.OPERATOR)
                addTypeVariable(typeVariable)
                addParameter("element", typeVariable)
                addStatement("return this?.nonNullPlus(element) ?: listOf(element)")
            }.build()
        )
        addFunction(
            FunSpec.builder("plus").apply {
                returns(nullableMapType)
                receiver(nullableMapType)
                addModifiers(KModifier.OPERATOR)
                addTypeVariables(mapTypeVariables)
                addParameter("element", pairType)
                addStatement("return this?.nonNullPlus(element) ?: mapOf(element)")
            }.build()
        )
    }

    private fun TypeSpec.Builder.addProperty(method: KFunction<*>) {
        val parameterType = method.arguments.single().type.asTypeName().copy(nullable = true)
        addProperty(
            PropertySpec.builder(method.name.decapitalize(), parameterType).apply {
                mutable()
                initializer("null")
            }.build()
        )
    }

    private fun TypeSpec.Builder.addPropertyForDuplicatedMethods(name: String, methods: List<KFunction<*>>) {
        val decapitalName = name.decapitalize()
        val capitalName = name.capitalize()

        // sealed class DuplicatedField
        val sealedType = TypeSpec.classBuilder(capitalName)
            .addModifiers(KModifier.SEALED)

        // var duplicatedField: DuplicatedField? = null
        val sealedClassName = ClassName("", capitalName)
        val prop = PropertySpec.builder(decapitalName, sealedClassName.copy(nullable = true)).apply {
            initializer("null")
            mutable(true)
        }.build()
        addProperty(prop)

        methods.forEach { func ->
            val parameterType = func.parameters.single { it.kind == KParameter.Kind.VALUE }.type
            val parameterClass = parameterType.classifier as KClass<*>
            // data class String(val value: kotlin.String) : DuplicatedField()
            val constructor = FunSpec.constructorBuilder()
                .addParameter("value", parameterType.asTypeName())
                .build()
            val clazz = TypeSpec.classBuilder(parameterClass.simpleName!!).apply {
                primaryConstructor(constructor)
                addProperty(
                    PropertySpec.builder("value", parameterType.asTypeName())
                        .initializer("value")
                        .build()
                )
                superclass(sealedClassName)
            }.build()
            sealedType.addType(clazz)

            // fun String.toDuplicatedField() = DuplicatedField.String(this)
            val converterFunc = FunSpec.builder("to$capitalName").apply {
                receiver(parameterType.asTypeName())
                returns(sealedClassName)
                addStatement("return $capitalName.${parameterClass.simpleName}(this)")
            }.build()
            addFunction(converterFunc)

            // operator fun DuplicatedField?.div(value: Int) = DuplicatedField.Int(value)
            val operatorFunc = FunSpec.builder("div").apply {
                addModifiers(KModifier.OPERATOR)
                receiver(sealedClassName.copy(nullable = true))
                addParameter("value", parameterType.asTypeName())
                returns(sealedClassName)
                addStatement("return $capitalName.${parameterClass.simpleName}(value)")
            }.build()
            addFunction(operatorFunc)
        }

        addType(sealedType.build())
    }

    private val KFunction<*>.arguments
        get() = parameters.filter { it.kind == KParameter.Kind.VALUE }

}