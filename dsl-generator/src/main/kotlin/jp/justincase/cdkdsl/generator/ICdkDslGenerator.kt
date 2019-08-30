package jp.justincase.cdkdsl.generator

import java.io.File

interface ICdkDslGenerator {
    fun run(classes: Sequence<Class<out Any>>, targetDir: File, moduleName: String)
}