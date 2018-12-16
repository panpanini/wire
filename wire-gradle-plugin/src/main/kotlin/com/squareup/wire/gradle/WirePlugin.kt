/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.squareup.wire.schema.Target
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import com.android.build.gradle.BasePlugin as AndroidBasePlugin

class WirePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("wire", WireExtension::class.java, project)

    var kotlin = false
    var android = false
    var java = false

    project.plugins.all {
      println("JROD: $it")
      when (it) {
        is AndroidBasePlugin<*> -> {
          android = true
          println("has android")
        }
        is JavaBasePlugin -> {
          java = true
          println("has java")
        }
        is KotlinBasePluginWrapper -> {
          kotlin = true
          println("has kotlin")
        }
      }
    }

    if (!android && !kotlin && !java) {
      throw IllegalArgumentException(
          "The Wire Gradle plugin requires either the Java, Kotlin or Android plugin to be applied prior to its being applied."
      )
    }

    project.afterEvaluate { project ->
      if (android) {
        applyAndroid(project, extension)
        return@afterEvaluate
      }

      if (kotlin) {
        applyKotlin()
        return@afterEvaluate
      }

      // TODO: what follows => applyJava()

      val sourceSets = extension.sourceSets ?: project.files("src/main/proto")
      val sourcePaths = extension.sourcePaths.asList()
          .map { path -> "${project.rootDir}/$path" }
      val protoPaths = extension.protoPaths?.asList() ?: sourcePaths

      val sourceDeps = project.configurations.create("wireSource").dependencies

      extension.sourcePaths2.forEach {
        sourceDeps.add(project.dependencies.create(it))
      }

      val targets = mutableListOf<Target>()
      val defaultBuildDirectory = "${project.buildDir}/generated/src/java"
      val outDirs = mutableListOf<String>()

      extension.javaTarget?.let { it ->
        val javaOut = it.outDirectory ?: defaultBuildDirectory
        outDirs += javaOut
        targets += Target.JavaTarget(
            elements = it.elements ?: listOf("*"),
            outDirectory = javaOut,
            android = it.android,
            androidAnnotations = it.androidAnnotations,
            compact = it.compact
        )
      }
      extension.kotlinTarget?.let { it ->
        val kotlinOut = it.outDirectory ?: defaultBuildDirectory
        outDirs += kotlinOut
        targets += Target.KotlinTarget(
            elements = it.elements ?: listOf("*"),
            outDirectory = kotlinOut,
            android = it.android,
            javaInterop = it.javaInterop
        )
      }

      val task = project.tasks.register("doWire", WireTask::class.java) {
        it.sourceFolders = sourceSets.files
        it.source(sourceSets)
        it.sourcePaths = sourcePaths
        it.protoPaths = protoPaths
        it.roots = extension.roots?.asList() ?: emptyList()
        it.prunes = extension.prunes?.asList() ?: emptyList()
        it.rules = extension.rules
        it.targets = targets
        it.group = "wire"
        it.description = "Generate Wire protocol buffer implementation for .proto files"
      }

      //project.tasks.named("compileKotlin").configure{ it.dependsOn(task) }
      val compileTask = project.tasks.named("compileJava") as TaskProvider<JavaCompile>
      compileTask.configure {
        it.setSource(outDirs)
        it.dependsOn(task)
      }
    }
  }

  private fun applyAndroid(project: Project, extension: WireExtension) {
    val variants: DomainObjectSet<out BaseVariant> = when {
      project.plugins.hasPlugin("com.android.application") -> {
        project.extensions.getByType(AppExtension::class.java).applicationVariants
      }
      project.plugins.hasPlugin("com.android.library") -> {
        project.extensions.getByType(LibraryExtension::class.java).libraryVariants
      }
      else -> {
        throw IllegalStateException("Unknown Android plugin in project '${project.path}'")
      }
    }

    applyAndroid(project, extension, variants)
  }

  private fun applyAndroid(
    project: Project,
    extension: WireExtension,
    variants: DomainObjectSet<out BaseVariant>
  ) {
    variants.all {
      val taskName = "generate${it.name.capitalize()}Protos"
      val taskProvider = project.tasks.register(taskName, WireTask::class.java) {
        it.group = "wire"
        it.description = "Generate Wire protocol buffer implementation for .proto files"
      }
      // TODO Use task configuration avoidance once released. https://issuetracker.google.com/issues/117343589
      //it.registerJavaGeneratingTask(taskProvider.get(), taskProvider.get().outputDirectory)
    }
  }

  private fun applyJava() {
    TODO("not implemented")
  }

  private fun applyKotlin() {
    TODO(
        "not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}