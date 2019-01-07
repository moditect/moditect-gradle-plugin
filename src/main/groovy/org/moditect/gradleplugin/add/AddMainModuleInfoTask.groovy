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
package org.moditect.gradleplugin.add

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.WorkResult
import org.moditect.commands.AddModuleInfo
import org.moditect.gradleplugin.Util
import org.moditect.gradleplugin.add.model.MainModuleConfiguration

import static org.gradle.util.ConfigureUtil.configure

@CompileStatic
class AddMainModuleInfoTask extends AbstractAddModuleInfoTask {
    private static final Logger LOGGER = Logging.getLogger(AddMainModuleInfoTask)

    @Input @Optional
    final Property<MainModuleConfiguration> mainModule

    AddMainModuleInfoTask() {
        description = 'Adds a module descriptor to the project JAR'
        mainModule = project.objects.property(MainModuleConfiguration)
    }

    void module(Closure closure) {
        LOGGER.info "calling module()"
        def cfg = new MainModuleConfiguration(project)
        configure(closure, cfg)
        mainModule.set(cfg)
    }

    @TaskAction
    void addModuleInfo() {
        def moduleCfg = mainModule.get()
        LOGGER.info "Adding moduleInfo to mainModule ${moduleCfg.moduleInfo}"

        Util.createDirectory(workingDirectory)
        Util.createDirectory(outputDirectory)
        Util.createDirectory(tmpDirectory)

        def inputJar = mainModule.get().inputJar
        def outputDir = outputDirectory.get().asFile
        new AddModuleInfo(
                getModuleInfoSource(moduleCfg),
                moduleCfg.mainClass,
                project.version as String,
                inputJar.toPath(),
                outputDir.toPath(),
                jvmVersion.present ? jvmVersion.get() : null,
                overwriteExistingFiles.get()
        ).run()
        copyModularizedJar(outputDir, inputJar)
    }

    @CompileDynamic
    private WorkResult copyModularizedJar(File outputDir, File inputJar) {
        project.copy {
            from "$outputDir/$inputJar.name"
            into inputJar.parentFile
        }
    }
}
