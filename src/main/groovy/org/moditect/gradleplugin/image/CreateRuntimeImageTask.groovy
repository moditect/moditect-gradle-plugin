/*
 * Copyright 2018 the original author or authors.
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
package org.moditect.gradleplugin.image

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.moditect.commands.CreateRuntimeImage
import org.moditect.gradleplugin.ModitectLog
import org.moditect.gradleplugin.Util

import java.nio.file.Path

import static org.moditect.gradleplugin.Util.createDirectoryProperty

@CompileStatic
class CreateRuntimeImageTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(CreateRuntimeImageTask)

    @Input @Optional
    final Property<String> jdkHome

    @OutputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty outputDirectory

    @Classpath @PathSensitive(PathSensitivity.RELATIVE)
    final ListProperty<File> modulePath

    @Input
    final ListProperty<String> modules

    @Input
    final Property<Launcher> launcher

    @Input
    final Property<Integer> compression

    @Input
    final Property<Boolean> stripDebug

    @Input
    final ListProperty<String> excludedResources

    @Input
    final Property<Boolean> ignoreSigningInformation

    static class Launcher implements Serializable {
        String name;
        String module;
    }

    CreateRuntimeImageTask() {
        jdkHome = project.objects.property(String)

        outputDirectory = createDirectoryProperty(project)
        outputDirectory.set(project.file("$project.buildDir/image"))

        modulePath = project.objects.listProperty(File)
        modulePath.set([new File(project.buildDir, 'modules')])

        modules = project.objects.listProperty(String)

        launcher = project.objects.property(Launcher)
        launcher.set(new Launcher())

        compression = project.objects.property(Integer)
        compression.set(0)

        stripDebug = project.objects.property(Boolean)
        stripDebug.set(false)

        excludedResources = project.objects.listProperty(String)
        excludedResources.set(new ArrayList<String>())

        ignoreSigningInformation = project.objects.property(Boolean)
        ignoreSigningInformation.set(false)
    }

    @TaskAction
    void createRuntime() {
        Set<Path> effectiveModulePath = modulePath.get().collect{f -> ((File)f).toPath()} as Set<Path>
        effectiveModulePath.add(JModsDir)

        project.delete(outputDirectory.get().asFile)

        new CreateRuntimeImage(
                effectiveModulePath,
                modules.get() as List<String>,
                launcher.get().name,
                launcher.get().module,
                outputDirectory.get().asFile.toPath(),
                compression.get(),
                stripDebug.get(),
                ignoreSigningInformation.get(),
                excludedResources.get() as List<String>,
                new ModitectLog()
        ).run();
    }

    private Path getJModsDir() {
        String javaHome = jdkHome.present ? jdkHome.get() : System.getProperty('java.home')
        new File(javaHome).toPath().resolve("jmods")
    }

    void launcher(Closure closure) {
        doLauncher(closure)
    }
    void launcher(Action<Launcher> action) {
        doLauncher(action)
    }
    private void doLauncher(Object actionOrClosure) {
        LOGGER.debug "calling launcher()"
        Util.executeActionOrClosure(launcher.get(), actionOrClosure)
    }
}
