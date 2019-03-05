/*
 * Copyright (C) 2016 Square, Inc.
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
package com.squareup.wire.java.internal

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.internal.parser.OptionElement
import com.squareup.wire.schema.internal.parser.OptionReader
import com.squareup.wire.schema.internal.parser.SyntaxReader

/** Parses `.wire` files.  */
class ProfileParser(
  private val location: Location,
  data: String
) {
  private val reader: SyntaxReader = SyntaxReader(data.toCharArray(), location)
  private val imports = mutableListOf<String>()
  private val typeConfigs = mutableListOf<TypeConfigElement>()

  /** Output package name, or null if none yet encountered.  */
  private var packageName: String? = null

  fun read(): ProfileFileElement {
    val label = reader.readWord()
    if (label != "syntax") throw reader.unexpected("expected 'syntax'")
    reader.require('=')
    val syntaxString = reader.readQuotedString()
    if (syntaxString != "wire2") throw reader.unexpected("expected 'wire2'")
    reader.require(';')

    while (true) {
      val documentation = reader.readDocumentation()
      if (reader.exhausted()) {
        return ProfileFileElement(
            location = location,
            packageName = packageName,
            imports = imports,
            typeConfigs = typeConfigs
        )
      }

      readDeclaration(documentation)
    }
  }

  private fun readDeclaration(documentation: String) {
    val location = reader.location()
    val label = reader.readWord()

    when (label) {
      "package" -> {
        if (packageName != null) throw reader.unexpected(location, "too many package names")
        packageName = reader.readName()
        reader.require(';')
      }
      "import" -> {
        val importString = reader.readString()
        imports.add(importString)
        reader.require(';')
      }
      "type" -> typeConfigs.add(readTypeConfig(location, documentation))
      else -> throw reader.unexpected(location, "unexpected label: $label")
    }
  }

  /** Reads a type config and returns it.  */
  private fun readTypeConfig(
    location: Location,
    documentation: String
  ): TypeConfigElement {
    val name = reader.readDataType()
    val withOptions = mutableListOf<OptionElement>()
    var target: String? = null
    var adapter: String? = null

    reader.require('{')
    while (!reader.peekChar('}')) {
      val wordLocation = reader.location()
      val word = reader.readWord()
      when (word) {
        "target" -> {
          if (target != null) throw reader.unexpected(wordLocation, "too many targets")
          target = reader.readWord()
          if (reader.readWord() != "using") throw reader.unexpected("expected 'using'")
          val adapterType = reader.readWord()
          reader.require('#')
          val adapterConstant = reader.readWord()
          reader.require(';')
          adapter = adapterType + '#'.toString() + adapterConstant
        }

        "with" -> {
          withOptions += OptionReader(reader).readOption('=')
          reader.require(';')
        }

        else -> throw reader.unexpected(wordLocation, "unexpected label: $word")
      }
    }

    return TypeConfigElement(
        location = location,
        type = name,
        documentation = documentation,
        with = withOptions,
        target = target,
        adapter = adapter
    )
  }
}
