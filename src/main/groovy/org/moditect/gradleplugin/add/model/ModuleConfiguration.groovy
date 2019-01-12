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
package org.moditect.gradleplugin.add.model

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.moditect.gradleplugin.ModitectPlugin
import org.moditect.gradleplugin.Util
import org.moditect.gradleplugin.common.ModuleId

@CompileStatic
class ModuleConfiguration extends AbstractModuleConfiguration {
    private static final Logger LOGGER = Logging.getLogger(ModuleConfiguration)

    static final String PRIMARY_CFG_PREFIX = 'moditectAddPrimary'
    static final String MODULE_CFG_PREFIX = 'moditectAddModule'

    private final int index

    @Input
    String moduleConfigName

    private Configuration moduleConfig
    private Configuration primaryConfig

    @Internal
    Dependency primaryDependency

    @Internal
    final Set<Dependency> additionalDependencies = []

    ModuleConfiguration(Project project, int index) {
        super(project)
        this.index = index
    }

    Dependency artifact(Object dependencyNotation, Closure closure) {
        doArtifact(dependencyNotation, closure)
    }
    Dependency artifact(Object dependencyNotation, Action<Dependency> action) {
        doArtifact(dependencyNotation, action)
    }
    private Dependency doArtifact(Object dependencyNotation, Object actionOrClosure) {
        Dependency dependency = artifact(dependencyNotation)
        Util.executeActionOrClosure(dependency, actionOrClosure)
        dependency
    }

    Dependency artifact(Object dependencyNotation) {
        LOGGER.info "Creating primaryDependency of $shortName from: $dependencyNotation"

        if(primaryConfig) throw new GradleException("Multiple artifact() calls in $shortName")

        def primaryConfigName = PRIMARY_CFG_PREFIX + index
        this.primaryConfig = project.configurations.create(primaryConfigName)

        def notation = Util.getAdjustedDependencyNotation(project, dependencyNotation)
        primaryDependency = project.dependencies.add(primaryConfigName, notation)
        if(primaryDependency instanceof ModuleDependency) {
            ((ModuleDependency)primaryDependency).transitive = false
        }

        this.moduleConfigName = MODULE_CFG_PREFIX + index
        this.moduleConfig = project.configurations.create(moduleConfigName)
        project.dependencies.add(moduleConfigName, notation)

        project.dependencies.add(ModitectPlugin.FULL_CONFIGURATION_NAME, notation)
    }

    @InputFile @PathSensitive(PathSensitivity.NONE)
    @Override
    File getInputJar() {
        primaryArtifact.file
    }

    @Input
    @Override
    String getVersion() {
        primaryArtifact.moduleVersion.id.version
    }

    private volatile ResolvedArtifact cachedPrimaryArtifact
    @Internal ResolvedArtifact getPrimaryArtifact() {
        if(!cachedPrimaryArtifact) {
            synchronized (this) {
                if(!cachedPrimaryArtifact) {
                    cachedPrimaryArtifact = retrievePrimaryArtifact()
                }
            }
        }
        cachedPrimaryArtifact
    }
    private ResolvedArtifact retrievePrimaryArtifact() {
        if(!primaryConfig) throw new GradleException("No artifact declaration found in $shortName")
        def artifacts = primaryConfig.resolvedConfiguration.resolvedArtifacts
        LOGGER.info "artifacts of $primaryConfig.name: $artifacts"
        artifacts.find { artifact -> !Util.isEmptyJar(artifact.file) }
    }

    private volatile Set<ResolvedArtifact> cachedModuleArtifacts
    @Internal Set<ResolvedArtifact> getModuleArtifacts() {
        if(!cachedModuleArtifacts) {
            synchronized (this) {
                if(!cachedModuleArtifacts) {
                    cachedModuleArtifacts = retrieveModuleArtifacts()
                }
            }
        }
        cachedModuleArtifacts
    }
    private Set<ResolvedArtifact> retrieveModuleArtifacts() {
        if(!moduleConfig) throw new GradleException("No artifact declaration found in $shortName")
        def artifacts = moduleConfig.resolvedConfiguration.resolvedArtifacts
        LOGGER.info "artifacts of $moduleConfigName: $artifacts"
        artifacts
    }

    private volatile Set<org.eclipse.aether.graph.Dependency> cachedAetherDependencies
    @Internal Set<org.eclipse.aether.graph.Dependency> getAetherDependencies() {
        if(!cachedAetherDependencies) {
            synchronized (this) {
                if(!cachedAetherDependencies) {
                    cachedAetherDependencies = retrieveAetherDependencies()
                }
            }
        }
        cachedAetherDependencies
    }
    private Set<org.eclipse.aether.graph.Dependency> retrieveAetherDependencies() {
        def dependencyResolver = Util.getModitectExtension(project).dependencyResolver
        dependencyResolver.getDependencies(primaryDependency.group, primaryDependency.name, version)
    }

    void updateFullConfiguration() {
        aetherDependencies.each {
            def a = it.artifact
            if(a.groupId == 'org.openjfx' && !a.classifier) {
                LOGGER.info("Not added to $ModitectPlugin.FULL_CONFIGURATION_NAME: $a")
            } else {
                def notation = "$a.groupId:$a.artifactId:$a.version"
                if(a.classifier) notation += ':' + Util.resolveClassifier(a.classifier)
                def dep = project.dependencies.add(ModitectPlugin.FULL_CONFIGURATION_NAME, notation)
                LOGGER.info("Added to $ModitectPlugin.FULL_CONFIGURATION_NAME: $dep")
            }
        }
    }

    Dependency additionalDependency(Object dependencyNotation, Closure closure) {
        doAdditionalDependency(dependencyNotation, closure)
    }
    Dependency additionalDependency(Object dependencyNotation, Action<Dependency> action) {
        doAdditionalDependency(dependencyNotation, action)
    }
    private Dependency doAdditionalDependency(Object dependencyNotation, Object actionOrClosure) {
        def dependency = additionalDependency(dependencyNotation)
        Util.executeActionOrClosure(dependency, actionOrClosure)
        dependency
    }

    Dependency additionalDependency(Object dependencyNotation) {
        LOGGER.info "Creating additionalDependency of $shortName from: $dependencyNotation"
        def dep = project.dependencies.add(ModitectPlugin.FULL_CONFIGURATION_NAME, dependencyNotation)
        additionalDependencies.add(dep)
        dep
    }

    @Input
    String getShortName() {
        "module #$index"
    }

    @Override
    @Input
    Set<ModuleId> getOptionalDependencies() {
        List<ModuleId> moduleIds = moduleArtifacts.collect{ new ModuleId(it.moduleVersion.id.group, it.moduleVersion.id.name) }
        Set<ModuleId> optionalModuleIds = []
        def optionalDependencies = aetherDependencies.findAll { it.optional }
        optionalDependencies.each { dep ->
            def moduleId = new ModuleId(dep.artifact.groupId, dep.artifact.artifactId)
            if(!moduleIds.contains(moduleId)) {
                optionalModuleIds << moduleId
            }
        }
        additionalDependencies.each { dep ->
            optionalModuleIds.add(new ModuleId(dep.group, dep.name))
        }
        LOGGER.info "optionalDependencies: $optionalDependencies"
        LOGGER.info "moduleIds: $moduleIds"
        LOGGER.info "optionalModuleIds: $optionalModuleIds"
        return optionalModuleIds
    }

    @Input @Optional
    String getModuleName() {
        String src = moduleInfoSource ?: moduleInfoFile ? moduleInfoFile.text : null
        if(src) {
            for(line in src.readLines()) {
                def matcher = line =~ /\s*module\s+((?:\w|\.)+).*/
                if(matcher.matches()) {
                    return matcher.group(1)
                }
            }
            throw new GradleException("Cannot retrieve module name from: $src")
        }
        moduleInfo?.name
    }
}
