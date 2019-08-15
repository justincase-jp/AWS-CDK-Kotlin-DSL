package jp.justincase.cdkdsl.generator

import com.google.common.reflect.ClassPath

fun main() {
    val cdkClasses = ClassPath.from(ClassLoader.getSystemClassLoader()).allClasses.asSequence()
        .filter { it.packageName.startsWith("software.amazon.awscdk") }
        .map { it.load() }

    genResourceConstructResource(cdkClasses)
}