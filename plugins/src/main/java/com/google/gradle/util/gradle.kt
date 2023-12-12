/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gradle.util

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.utils.provider

/** Creates a file [Provider] mapped for the build directory. */
fun Project.buildDir(path: String) = layout.buildDirectory.asFile.childFile(path)

/**
 * Submits a piece of work to be executed asynchronously.
 *
 * More Kotlin friendly variant of the existing [WorkQueue.submit]
 *
 * Syntax sugar for:
 * ```kotlin
 * submit(T::class.java, paramAction)
 * ```
 */
inline fun <reified T : WorkAction<C>, C : WorkParameters> WorkQueue.submit(
  noinline action: C.() -> Unit
) {
  submit(T::class.java, action)
}

/**
 * Maps a file provider to another file provider as a sub directory.
 *
 * Syntax sugar for:
 * ```
 * fileProvider.map { File("${it.path}/$path") }
 * ```
 */
fun Provider<File>.childFile(path: String) = map { File("${it.path}/$path") }

/**
 * Returns a new [File] under the given sub directory.
 *
 * Syntax sugar for:
 * ```
 * File("$path/$childPath")
 * ```
 */
fun File.childFile(childPath: String) = File("$path/$childPath")

/**
 * Provides a temporary file for use during the task.
 *
 * Creates a file under the [temporaryDir][DefaultTask.getTemporaryDir] of the task, and should be
 * preferred to defining an explicit [File]. This will allow Gradle to make better optimizations on
 * our part, and helps us avoid edge-case scenarios like conflicting file names.
 */
fun DefaultTask.tempFile(path: String): Provider<File> =
  project.provider { temporaryDir.childFile(path) }

/**
 * Syntax sugar for:
 * ```kotlin
 * plugins.apply(T::class)
 * ```
 */
inline fun <reified T : Plugin<*>> PluginContainer.apply() = apply(T::class)

/**
 * Represents an exception used to skip a Gradle task.
 *
 * Provides a more semantic way to refer to [StopActionException] when skipping tasks; as folks seem
 * to infer from the exception name that it stops *all* execution- when that's not the case.
 */
typealias SkipTask = StopActionException

/**
 * Retrieves the output file of a Gradle task, as a [Provider].
 *
 * Allows for easy access to the primary output file of a task, when tasks output a directory
 * instead of a single file. It filters out directories and returns the first file in the task's
 * outputs.
 */
val TaskProvider<*>.outputFile: Provider<File>
  get() = map { it.outputs.files.asFileTree.first { !it.isDirectory } }

/** The Android extension specific for Kotlin projects within Gradle. */
val Project.android: KotlinAndroidProjectExtension
  get() = extensions.getByType<KotlinAndroidProjectExtension>()

/**
 * The `"release"` compilation target of a Kotlin Android project.
 *
 * In non android projects, this would be referred to as the main source set.
 */
val KotlinAndroidProjectExtension.release: KotlinJvmAndroidCompilation
  get() = target.compilations.getByName("release")

/**
 * Provides a project property as the specified type, or null otherwise.
 *
 * Utilizing a safe cast, an attempt is made to cast the project property (if found) to the receiver
 * type. Should this cast fail, the resulting value will be null.
 *
 * Keep in mind that is provided lazily via a [Provider], so evaluation does not occur until the
 * value is needed.
 *
 * @param property the name of the property to look for
 */
inline fun <reified T> Project.provideProperty(property: String) = provider {
  findProperty(property) as? T
}