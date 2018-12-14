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

import com.android.build.gradle.BasePlugin
import com.squareup.wire.schema.Target
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper

class WirePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("wire", WireExtension::class.java, project)

    var kotlin = false
    var android = false
    var java = false

    project.plugins.all {
      when (it) {
        is KotlinBasePluginWrapper -> {
          kotlin = true
          println("has kotlin")
        }
        is BasePlugin<*> -> {
          android = true
          println("has android")
        }
        is JavaBasePlugin -> {
          java = true
          println("has java")
        }
      }
    }

    project.afterEvaluate { project ->
      val sourceSets = extension.sourceSets ?: project.files("src/main/proto")
      val sourcePaths = extension.sourcePaths.asList()
          .map { path -> "${project.rootDir}/$path" }
      val protoPaths = extension.protoPaths?.asList() ?: sourcePaths

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
}