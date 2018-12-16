package com.squareup.wire.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.Test
import java.io.File

class PluginTest {
  @Test
  fun foo() {
    val fixtureRoot = File("..")
    val runner = GradleRunner.create()
        .withProjectDir(fixtureRoot)
        .withPluginClasspath()

    val result = runner
        .withArguments("assembleSample", "--stacktrace")
        .buildAndFail()

    assertThat(result.output)
        .contains(
            "SQL Delight Gradle plugin applied in project ':' but no supported Kotlin plugin was found")
  }
}