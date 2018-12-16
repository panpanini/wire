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
package com.squareup.wire.gradle;

import java.io.File;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

public class WireExtension {
  private FileCollection sourceSets;
  private String[] sourcePaths;
  private String[] sourcePaths2;
  private String[] protoPaths;
  private String[] roots;
  private String[] prunes;
  private File rules;
  private JavaTarget javaTarget;
  private KotlinTarget kotlinTarget;

  public WireExtension(Project project) {
    ObjectFactory objectFactory = project.getObjects();
    javaTarget = objectFactory.newInstance(JavaTarget.class);
    kotlinTarget = objectFactory.newInstance(KotlinTarget.class);
  }

  @InputFiles
  @Optional
  public FileCollection getSourceSets() {
    return sourceSets;
  }

  public void setSourceSets(FileCollection sourceSets) {
    this.sourceSets = sourceSets;
  }

  @InputFiles
  public String[] getSourcePaths() {
    return sourcePaths;
  }

  public void setSourcePaths(String[] sourcePaths) {
    this.sourcePaths = sourcePaths;
  }

  @InputFiles
  public String[] getSourcePaths2() {
    return sourcePaths2;
  }

  public void setSourcePaths2(String[] sourcePaths) {
    this.sourcePaths2 = sourcePaths;
  }

  @InputFiles
  @Optional
  public String[] getProtoPaths() {
    return protoPaths;
  }

  public void setProtoPaths(String[] protoPaths) {
    this.protoPaths = protoPaths;
  }

  @Input
  @Optional
  public String[] getRoots() {
    return roots;
  }

  public void setRoots(String[] roots) {
    this.roots = roots;
  }

  @Input
  @Optional
  public String[] getPrunes() {
    return prunes;
  }

  public void setPrunes(String[] prunes) {
    this.prunes = prunes;
  }

  @Input
  @Optional
  public File getRules() {
    return rules;
  }

  public void setRules(File rules) {
    this.rules = rules;
  }

  @Input
  @Optional
  public JavaTarget getJavaTarget() {
    return javaTarget;
  }

  public void javaTarget(Action<JavaTarget> action) {
    action.execute(javaTarget);
  }

  @Input
  @Optional
  public KotlinTarget getKotlinTarget() {
    return kotlinTarget;
  }

  public void kotlinTarget(Action<KotlinTarget> action) {
    action.execute(kotlinTarget);
  }

  static class JavaTarget {
    @Inject public JavaTarget() {
    }

    public List<String> elements;
    public String outDirectory;
    public boolean android;
    public boolean androidAnnotations;
    public boolean compact;
  }

  static class KotlinTarget {
    @Inject public KotlinTarget() {
    }

    public String outDirectory;
    public List<String> elements;
    public boolean android;
    public boolean javaInterop;
  }
}
