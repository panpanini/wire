/*
 * Copyright 2018 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire.schema

import com.squareup.wire.ConsoleWireLogger
import com.squareup.wire.WireException
import com.squareup.wire.WireLogger
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.FileSystems

/**
 * An invocation of the Wire compiler. Each invocation performs the following operations:
 *
 *  1. Read source `.proto` files directly from the file system or from archive files (ie. `.jar`
 *     and `.zip` files). This will also load imported `.proto` files from either the [sourcePath]
 *     or [protoPath]. The collection of loaded type declarations is called a schema.
 *
 *  2. Validate the schema and resolve references between types.
 *
 *  3. Optionally prune the schema. This builds a new schema that is a subset of the original. The
 *     new schema contains only types that are both transitively reachable from [treeShakingRoots]
 *     and not in [treeShakingRubbish].
 *
 *  4. Call each target. It will generate sources for protos in the [sourcePath] that are in its
 *     [Target.elements] and that haven't already been emitted by an earlier target.
 *
 *
 * Source Directories and Archives
 * -------------------------------
 *
 * The [sourcePath] and [protoPath] lists contain locations that are of the following forms:
 *
 *  * Locations of `.proto` files.
 *
 *  * Locations of directories that contain a tree of `.proto` files. Typically this is a directory
 *    ending in `src/main/proto`.
 *
 *  * Locations of `.zip` and `.jar` archives that contain a tree of `.proto` files. Typically this
 *    is a `.jar` file from a Maven repository.
 *
 * When one `.proto` message imports another, the import is resolved from the base of each location
 * and archive. If the build is in the unfortunate situation where an import could be resolved by
 * multiple files, whichever was listed first takes precedence.
 *
 * Although the content and structure of [sourcePath] and [protoPath] are the same, only types
 * defined in [sourcePath] are used to generated sources.
 *
 *
 * Matching Packages, Types, and Members
 * -------------------------------------
 *
 * The [treeShakingRoots], [treeShakingRubbish], and [Target.elements] lists contain strings that
 * select proto types and members. Strings in these lists are in one of these forms:
 *
 *  * Package names followed by `.*`, like `squareup.dinosaurs.*`. This matches types defined in the
 *    package and its descendant packages. A lone asterisk `*` matches all packages.
 *
 *  * Fully-qualified type names like `squareup.dinosaurs.Dinosaur`. Types may be messages, enums,
 *    or services.
 *
 *  * Fully-qualified member names like `squareup.dinosaurs.Dinosaur#name`. These are type names
 *    followed by `#` followed by a member name. Members may be message fields, enum constants, or
 *    service RPCs.
 *
 * It is an error to specify mutually-redundant values in any of these lists. For example, the list
 * `[squareup.dinosaurs, squareup.dinosaurs.Dinosaur]` is invalid because the second element is
 * already matched by the first.
 *
 * Every element in each lists must apply to at least one declaration. Otherwise that option is
 * unnecessary and a possible typo.
 *
 *
 * Composability
 * -------------
 *
 * There are many moving parts in this system! For most applications it is safe to use [sourcePath]
 * and [targets] only. The other options are for the benefit of large and modular applications.
 *
 * ### Use [protoPath] when one proto module depends on another proto module.
 *
 * These `.proto` files are used for checking dependencies only. It is assumed that the sources for
 * these protos are generated elsewhere.
 *
 * ### Use tree shaking to remove unwanted types.
 *
 * [Tree shaking](https://en.wikipedia.org/wiki/Tree_shaking) can be used to create a
 * small-as-possible generated footprint even if the source declarations are large. This works like
 * [ProGuard](https://en.wikipedia.org/wiki/ProGuard_(software)) and other code shrinking compilers:
 * it allows you to benefit from a shared codebase without creating a large artifact.
 *
 * ### Use multiple targets to split generated code across multiple programming languages.
 *
 * If your project is already using generated Java, it’s difficult to switch to generated Kotlin.
 * Instead of switching everything over at once you can use multiple targets to switch over
 * incrementally. Targets consume their types; subsequent targets get whatever types are left over.
 */
data class WireRun(
  /**
   * Source `.proto` files for this task to generate from.
   */
  val sourcePath: List<Location>,

  /**
   * Sources `.proto` files for this task to use when resolving references.
   */
  val protoPath: List<Location> = listOf(),

  /**
   * The roots of the schema model. Wire will prune the schema model to only include types in this
   * list and the types transitively required by them.
   *
   * If a member is included in this list then the enclosing type is included but its other members
   * are not. For example, if `squareup.dinosaurs.Dinosaur#name` is in this list then the emitted
   * source of the `Dinosaur` message will have the `name` field, but not the `length_meters` or
   * `mass_kilograms` fields.
   */
  val treeShakingRoots: List<String> = listOf("*"),

  /**
   * Types and members that will be stripped from the schema model. Wire will remove the elements
   * themselves and also all references to them.
   */
  val treeShakingRubbish: List<String> = listOf(),

  /**
   * Action to take with the loaded, resolved, and possibly-pruned schema.
   */
  val targets: List<Target>
) {

  fun execute(fs: FileSystem = FileSystems.getDefault(), logger: WireLogger = ConsoleWireLogger()) {
    // 1. Read source `.proto` files.
    val schemaLoader = NewSchemaLoader(fs, sourcePath, protoPath)
    val protoFiles = schemaLoader.load()

    // 2. Load descriptor proto
    val protoFilesPlusDescriptor = protoFiles.plus(schemaLoader.loadDescriptorProto())

    // 3. Validate the schema and resolve references
    val fullSchema = Linker(protoFilesPlusDescriptor).link()

    // 4. Optionally prune the schema.
    val schema = treeShake(fullSchema, logger)

    // 5. Call each target.
    val typesToHandle = mutableListOf<Type>()
    for (protoFile in schema.protoFiles()) {
      if (schemaLoader.sourceLocationPaths.contains(protoFile.location().path())) {
        typesToHandle += protoFile.types()
      }
    }
    for (target in targets) {
      val typeHandler = target.newHandler(schema, fs, logger)

      val identifierSet = IdentifierSet.Builder()
          .include(target.elements)
          .build()

      val i = typesToHandle.iterator()
      while (i.hasNext()) {
        val type = i.next()
        if (identifierSet.includes(type.type())) {
          typeHandler.handle(type)
          i.remove()
        }
      }

      for (rule in identifierSet.unusedIncludes()) {
        logger.info("Unused element in target elements: $rule")
      }
    }
  }

  /** Returns a subset of schema with unreachable and unwanted elements removed. */
  private fun treeShake(schema: Schema, logger: WireLogger): Schema {
    if (treeShakingRoots == listOf("*") && treeShakingRubbish.isEmpty()) return schema

    val identifierSet = IdentifierSet.Builder()
        .include(treeShakingRoots)
        .exclude(treeShakingRubbish)
        .build()

    val result = schema.prune(identifierSet)

    for (rule in identifierSet.unusedIncludes()) {
      logger.info("Unused element in treeShakingRoots: $rule")
    }

    for (rule in identifierSet.unusedExcludes()) {
      logger.info("Unused element in treeShakingRubbish: $rule")
    }

    return result
  }

  companion object {
    @Throws(IOException::class)
    @JvmStatic fun main(args: Array<String>) {
      try {
        val wireCompiler = WireRun(
            // 1. PASS: if a jar, traverse the zip tree
//            sourcePath = listOf(
//                Location.get(
//                    "/Users/jrod/.gradle/caches/modules-2/files-2.1/com.squareup.protos/all-protos/20190301.010153/645918ca71f4e795b72ab87798d133e98c238494/all-protos-20190301.010153.jar"
//                )
//            ),
            // 2. PASS: if a directory, recursively scan all
            sourcePath = listOf(
                Location.get("sample/src/main/proto")
            ),
            // 3. PASS: can split between base and (import) path
//            sourcePath = listOf(
//                Location.get("sample/src/main/proto", "squareup/geology/period.proto")
//            ),
            // 4. PASS: ...or not!
//            sourcePath = listOf(
//                Location.get("sample/src/main/proto/squareup/geology/period.proto")
//            ),
            // 5. PASS: can refer to multiple
//            sourcePath = listOf(
//                Location.get("sample/src/main/proto", "squareup/geology/period.proto"),
//                Location.get("sample/src/main/proto", "squareup/dinosaurs/dinosaur.proto")
//            ),
            // 6. PASS: order doesn't matter, when split
//            sourcePath = listOf(
//                Location.get("sample/src/main/proto", "squareup/dinosaurs/dinosaur.proto"),
//                Location.get("sample/src/main/proto", "squareup/geology/period.proto")
//            ),
            // 7. PASS: proto path
//            sourcePath = listOf(
//                Location.get("sample/src/main/proto", "squareup/dinosaurs/dinosaur.proto")
//            ),
//            protoPath = listOf(
//                Location.get("sample/src/main/proto", "squareup/geology/period.proto")
//            ),
            // 8. FAIL: order matter when not split?
//            sourcePath = listOf(
//                Location.get("sample/src/main/proto/squareup/dinosaurs/dinosaur.proto"),
//                Location.get("sample/src/main/proto/squareup/geology/period.proto")
//            ),
            // 9. FAIL: nope, this doesn't work either...
//            sourcePath = listOf(
//                Location.get("sample/src/main/proto/squareup/geology/period.proto"),
//                Location.get("sample/src/main/proto/squareup/dinosaurs/dinosaur.proto")
//            ),

            targets = listOf(Target.JavaTarget(outDirectory = "sample/build/generated/src/main/java")),
            treeShakingRubbish = listOf(
                "vitess.*",
                "squareup.tracon.*",
                "squareup.privacyvault.service.*"
            )
        )
        wireCompiler.execute()
      } catch (e: WireException) {
        System.err.print("Fatal: ")
        e.printStackTrace(System.err)
        System.exit(1)
      }
    }
  }
}
