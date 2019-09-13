package jp.justincase.cdkdsl.example

import software.amazon.awscdk.core.App

fun main() {
    App().apply {
        exampleStack()
        synth()
    }
}