package jp.justincase.cdkdsl.generator

import kotlinx.coroutines.flow.Flow
import java.io.File

interface ICdkDslGenerator {
    suspend fun run(classes: Flow<Class<out Any>>, targetDir: File, moduleName: String, packageName: String)
}