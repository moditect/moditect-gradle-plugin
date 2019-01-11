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
package org.moditect.gradleplugin.generate.model

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
import org.moditect.gradleplugin.ModitectPlugin
import org.moditect.gradleplugin.Util
import org.moditect.gradleplugin.common.ModuleId
import org.moditect.gradleplugin.common.ModuleInfoConfiguration

@CompileStatic
class ModuleConfiguration {
    private static final Logger LOGGER = Logging.getLogger(ModuleConfiguration)

    static final String PRIMARY_CFG_PREFIX = 'moditectGenPrimary'

    private final Project project
    private final int index

    Dependency primaryDependency
    private Configuration primaryConfig

    final ModuleInfoConfiguration moduleInfo = new ModuleInfoConfiguration()

    final Set<Dependency> additionalDependencies = []

    ModuleConfiguration(Project project, int index) {
        this.project = project
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

        def basicConfigName = PRIMARY_CFG_PREFIX + index
        this.primaryConfig = project.configurations.create(basicConfigName)

        def notation = Util.getAdjustedDependencyNotation(project, dependencyNotation)
        primaryDependency = project.dependencies.add(basicConfigName, notation)
        if(primaryDependency instanceof ModuleDependency) {
            ((ModuleDependency)primaryDependency).transitive = false
        }
        project.dependencies.add(ModitectPlugin.FULL_CONFIGURATION_NAME, notation)
    }

    File getInputJar() {
        if(!primaryConfig) throw new GradleException("No artifact declaration found in $shortName")
        def artifacts = primaryConfig.resolvedConfiguration.resolvedArtifacts
        LOGGER.info "artifacts of $primaryConfig.name: $artifacts"
        artifacts.find { artifact -> !Util.isEmptyJar(artifact.file) }.file
    }

    @Lazy private volatile Set<ResolvedArtifact> moduleArtifacts = { retrieveModuleArtifacts() }()
    private Set<ResolvedArtifact> retrieveModuleArtifacts() {
        if(!primaryConfig) throw new GradleException("No artifact declaration found in $shortName")
        def artifacts = primaryConfig.resolvedConfiguration.resolvedArtifacts
        LOGGER.info "artifacts of $primaryConfig.name: $artifacts"
        artifacts
    }

    @Lazy private volatile Set<org.eclipse.aether.graph.Dependency> aetherDependencies = { retrieveAetherDependencies() }()
    private Set<org.eclipse.aether.graph.Dependency> retrieveAetherDependencies() {
        def dependencyResolver = Util.getModitectExtension(project).dependencyResolver
        dependencyResolver.getDependencies(primaryDependency.group, primaryDependency.name, primaryDependency.version)
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

    void moduleInfo(Closure closure) {
        doModuleInfo(closure)
    }
    void moduleInfo(Action<ModuleInfoConfiguration> action) {
        doModuleInfo(action)
    }
    void doModuleInfo(Object actionOrClosure) {
        LOGGER.debug "calling moduleInfo()"
        Util.executeActionOrClosure(moduleInfo, actionOrClosure)
    }

    String getShortName() {
        "module #$index"
    }

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

    @Override
    String toString() {
        shortName
    }
}
