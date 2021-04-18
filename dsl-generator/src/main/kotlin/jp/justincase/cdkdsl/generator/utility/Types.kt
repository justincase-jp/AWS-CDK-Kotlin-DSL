package jp.justincase.cdkdsl.generator.utility

fun List<Class<out Any>>.superTypesOf(other: Array<Class<out Any>>) =
    size == other.size && asSequence().zip(other.asSequence()){ a, b -> a.isAssignableFrom(b) }.all { it }
