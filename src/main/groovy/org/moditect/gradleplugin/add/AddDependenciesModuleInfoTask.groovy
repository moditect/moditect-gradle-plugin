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

import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.moditect.commands.AddModuleInfo
import org.moditect.gradleplugin.Util
import org.moditect.gradleplugin.add.model.ModuleConfiguration
import org.moditect.gradleplugin.common.ModuleId

@CompileStatic
class AddDependenciesModuleInfoTask extends AbstractAddModuleInfoTask {
    private static final Logger LOGGER = Logging.getLogger(AddDependenciesModuleInfoTask)

    // TODO - make ModuleConfiguration serializable and annotate with @Input
    @Internal
    final Property<ModuleList> modules

    @Internal
    @Lazy volatile Map<ModuleId, String> assignedNamesByModules = { retrieveAssignedNamesByModules() }()

    @ToString(includeNames = true)
    static class ModuleList implements Serializable {
        transient private final Project project
        final List<ModuleConfiguration> moduleConfigurations = []

        ModuleList(Project project) {
            this.project = project
        }

        ModuleConfiguration module(Closure closure) {
            doModule(closure)
        }
        ModuleConfiguration module(Action<ModuleConfiguration> action) {
            doModule(action)
        }
        private ModuleConfiguration doModule(Object actionOrClosure) {
            int index = moduleConfigurations.size()
            def moduleCfg = new ModuleConfiguration(project, index)
            moduleConfigurations.add(moduleCfg)

            LOGGER.info "Calling action of $moduleCfg.shortName"
            Util.executeActionOrClosure(moduleCfg, actionOrClosure)
            LOGGER.info "moduleCfg #$index: $moduleCfg"
            moduleCfg
        }
    }

    AddDependenciesModuleInfoTask() {
        modules = project.objects.property(ModuleList)
        modules.set(new ModuleList(project))
    }

    void modules(Closure closure) {
        doModules(closure)
    }
    void modules(Action<ModuleList> action) {
        doModules(action)
    }
    private void doModules(Object actionOrClosure) {
        LOGGER.debug "calling modules()"
        Util.executeActionOrClosure(modules.get(), actionOrClosure)
    }

    @TaskAction
    void addModuleInfo() {
        LOGGER.info "Adding moduleInfo to ${modules.get().moduleConfigurations?.collect {it.inputJar.name}}"

        Util.createDirectory(workingDirectory)
        Util.createDirectory(outputDirectory)
        Util.createDirectory(tmpDirectory)

        for(ModuleConfiguration moduleCfg : modules.get().moduleConfigurations) {
            new AddModuleInfo(
                    getModuleInfoSource(moduleCfg),
                    moduleCfg.mainClass,
                    moduleCfg.version,
                    moduleCfg.inputJar.toPath(),
                    outputDirectory.get().asFile.toPath(),
                    jvmVersion.present ? jvmVersion.get() : null,
                    overwriteExistingFiles.get()
            ).run()
        }
    }

    private Map<ModuleId, String> retrieveAssignedNamesByModules() {
        Map<ModuleId, String> assignedNamesByModule = [:]
        for (ModuleConfiguration moduleCfg : modules.get().moduleConfigurations) {
            def name = moduleCfg.moduleName
            def dep = moduleCfg.primaryDependency
            if (name && dep) {
                assignedNamesByModule[new ModuleId(group: dep.group, name: dep.name)] = name
            }
        }
        assignedNamesByModule
    }
}
