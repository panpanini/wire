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

import com.squareup.wire.schema.Target
import com.squareup.wire.schema.WireRun
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import java.io.File

open class WireTask : SourceTask() {
  @Input fun pluginVersion() = ""

  lateinit var sourceFolders: Iterable<File>
  lateinit var sourcePaths: List<String>
  lateinit var protoPaths: List<String>
  lateinit var roots: List<String>
  lateinit var prunes: List<String>
  lateinit var targets: List<Target>

  @TaskAction
  fun generateWireFiles() {
    val wireRun = WireRun(
        sourcePath = sourcePaths,
        protoPath = protoPaths,
        treeShakingRoots = roots,
        treeShakingRubbish = prunes,
        targets = targets
    )

    wireRun.execute()
  }
}