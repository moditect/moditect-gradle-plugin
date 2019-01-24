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
package org.moditect.gradleplugin

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.moditect.commands.GenerateModuleInfo
import org.moditect.gradleplugin.add.AddDependenciesModuleInfoTask
import org.moditect.gradleplugin.add.AddMainModuleInfoTask
import org.moditect.gradleplugin.aether.DependencyResolver
import org.moditect.gradleplugin.common.ArtifactDescriptor
import org.moditect.gradleplugin.common.ModuleId
import org.moditect.gradleplugin.generate.GenerateModuleInfoTask
import org.moditect.gradleplugin.image.CreateRuntimeImageTask
import org.moditect.model.DependencyDescriptor

import static org.moditect.gradleplugin.Util.createDirectoryProperty

@CompileStatic
class ModitectExtension {
    private static final Logger LOGGER = Logging.getLogger(GenerateModuleInfoTask)

    private final Project project

    final DirectoryProperty workingDirectory

    AddMainModuleInfoTask addMainModuleInfoTask
    AddDependenciesModuleInfoTask addDependenciesModuleInfoTask
    GenerateModuleInfoTask generateModuleInfoTask
    CreateRuntimeImageTask createRuntimeImageTask

    final DependencyResolver dependencyResolver

    @Lazy volatile ArtifactsInfo artifactsInfo = { retrieveArtifactsInfo() }()

    static class ArtifactsInfo {
        final Map<ModuleId, String> assignedNamesByModules
        final Set<ArtifactDescriptor> fullFixedArtifactDescriptors

        ArtifactsInfo(Map<ModuleId, String> assignedNamesByModules, Set<ArtifactDescriptor> fullFixedArtifactDescriptors) {
            this.assignedNamesByModules = assignedNamesByModules
            this.fullFixedArtifactDescriptors = fullFixedArtifactDescriptors
        }
    }

    ModitectExtension(Project project) {
        this.project = project
        this.workingDirectory = createDirectoryProperty(project)
        this.workingDirectory.set(project.file("$project.buildDir/moditect"))
        this.dependencyResolver = new DependencyResolver(project)
    }

    void init(
            AddMainModuleInfoTask addMainModuleInfoTask,
            AddDependenciesModuleInfoTask addDependenciesModuleInfoTask,
            GenerateModuleInfoTask generateModuleInfoTask,
            CreateRuntimeImageTask createRuntimeImageTask) {
        this.addMainModuleInfoTask = addMainModuleInfoTask
        this.addDependenciesModuleInfoTask = addDependenciesModuleInfoTask
        this.generateModuleInfoTask = generateModuleInfoTask
        this.createRuntimeImageTask = createRuntimeImageTask

        Util.createDirectory(workingDirectory)
        def workingDirAsFile = workingDirectory.get().asFile
        this.addMainModuleInfoTask.workingDirectory.set(workingDirAsFile)
        this.addDependenciesModuleInfoTask.workingDirectory.set(workingDirAsFile)
        this.generateModuleInfoTask.workingDirectory.set(workingDirAsFile)
    }

    private ArtifactsInfo retrieveArtifactsInfo() {
        addDependenciesModuleInfoTask.modules.get().moduleConfigurations.each { cfg ->
            cfg.updateFullConfiguration()
        }
        generateModuleInfoTask.modules.get().moduleConfigurations.each { cfg ->
            cfg.updateFullConfiguration()
        }
        def cfg = project.configurations.getByName(ModitectPlugin.FULL_CONFIGURATION_NAME)
        cfg.extendsFrom(project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME))
        cfg.canBeResolved = true

        Map<ModuleId, String> assignedNamesByModules = [:]
        assignedNamesByModules.putAll(generateModuleInfoTask.assignedNamesByModules)
        assignedNamesByModules.putAll(addDependenciesModuleInfoTask.assignedNamesByModules)

        Set<ArtifactDescriptor> descriptors = []
        cfg.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            String autoModuleName = DependencyDescriptor.getAutoModuleNameFromInputJar(artifact.file.toPath(), null);
            def info = artifact.moduleVersion.id
            File inputJar
            if (autoModuleName != null) {
                inputJar = artifact.file
            } else {
                String moduleName = assignedNamesByModules.get(new ModuleId(info.group, info.name))
                inputJar = GenerateModuleInfo.createCopyWithAutoModuleNameManifestHeader(
                        workingDirectory.get().asFile.toPath(),
                        artifact.file.toPath(),
                        moduleName
                ).toFile()
            }
            descriptors.add(new ArtifactDescriptor(info.group, info.name, inputJar))
        }
        LOGGER.info "assignedNamesByModules: ${assignedNamesByModules.collect {'\n\t' + it}}"
        LOGGER.info "artifactDescriptors: ${descriptors.collect {'\n\t' + it}}"
        new ArtifactsInfo(assignedNamesByModules, descriptors)
    }


    void addMainModuleInfo(Action<AddMainModuleInfoTask> action) {
        action.execute(addMainModuleInfoTask)
    }

    void addDependenciesModuleInfo(Action<AddDependenciesModuleInfoTask> action) {
        action.execute(addDependenciesModuleInfoTask)
    }

    void generateModuleInfo(Action<GenerateModuleInfoTask> action) {
        action.execute(generateModuleInfoTask)
    }

    void createRuntimeImage(Action<CreateRuntimeImageTask> action) {
        action.execute(createRuntimeImageTask)
    }
}
