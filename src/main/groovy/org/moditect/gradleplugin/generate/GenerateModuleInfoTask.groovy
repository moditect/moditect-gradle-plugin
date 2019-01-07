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
package org.moditect.gradleplugin.generate

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.moditect.gradleplugin.ModitectExtension
import org.moditect.gradleplugin.Util
import org.moditect.gradleplugin.common.ModuleId
import org.moditect.gradleplugin.generate.model.ModuleConfiguration

import static org.gradle.util.ConfigureUtil.configure
import static org.moditect.gradleplugin.Util.createDirectoryProperty

@CompileStatic
class GenerateModuleInfoTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(GenerateModuleInfoTask)

    @OutputDirectory
    final DirectoryProperty workingDirectory

    @OutputDirectory
    final DirectoryProperty outputDirectory

    @Input @Optional
    final ListProperty<String> jdepsExtraArgs

    // TODO - make ModuleConfiguration serializable and annotate with @Input
    @Internal
    final Property<ModuleList> modules

    @Internal
    @Lazy volatile Map<ModuleId, String> assignedNamesByModules = { retrieveAssignedNamesByModules() }()

    static class ModuleList implements Serializable {
        transient private final Project project
        final List<ModuleConfiguration> moduleConfigurations = []

        ModuleList(Project project) {
            this.project = project
        }

        ModuleConfiguration module(Closure closure) {
            int index = moduleConfigurations.size()
            def moduleCfg = new ModuleConfiguration(project, index, closure)
            moduleConfigurations.add(moduleCfg)
            LOGGER.info "moduleCfg #$index: $moduleCfg"
            moduleCfg
        }
    }

    GenerateModuleInfoTask() {
        description = 'Generates module descriptors'
        group = 'moditect'

        workingDirectory = createDirectoryProperty(project)

        outputDirectory = createDirectoryProperty(project)
        outputDirectory.set(project.file("$project.buildDir/generated-sources/modules"))

        modules = project.objects.property(ModuleList)
        modules.set(new ModuleList(project))

        jdepsExtraArgs = project.objects.listProperty(String)
    }

    void modules(Closure modulesClosure) {
        LOGGER.info "calling modules()"
        configure(modulesClosure, modules.get())
    }

    @TaskAction
    void generateModuleInfo() {
        LOGGER.info "Generating moduleInfo for: ${modules.get()}"
        Util.createDirectory(workingDirectory)
        Util.createDirectory(outputDirectory)

        def artifactsInfo  = Util.getModitectExtension(project).artifactsInfo
        for(ModuleConfiguration moduleCfg : modules.get().moduleConfigurations) {
            Util.generateModuleInfo(
                    workingDirectory.get().asFile,
                    outputDirectory.get().asFile,
                    moduleCfg.moduleInfo,
                    moduleCfg.inputJar,
                    Util.getDependencyDescriptors(
                            artifactsInfo.fullFixedArtifactDescriptors,
                            artifactsInfo.assignedNamesByModules,
                            moduleCfg.optionalDependencies
                    ),
                    jdepsExtraArgs.get() as List<String>
            )
        }
    }

    private Map<ModuleId, String> retrieveAssignedNamesByModules() {
        Map<ModuleId, String> assignedNamesByModule = [:]
        for (ModuleConfiguration moduleCfg : modules.get().moduleConfigurations) {
            def name = moduleCfg.moduleInfo?.name
            def dep = moduleCfg.primaryDependency
            if (name && dep) {
                assignedNamesByModule[new ModuleId(group: dep.group, name: dep.name)] = name
            }
        }
        assignedNamesByModule
    }
}
