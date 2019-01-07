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

import com.google.gradle.osdetector.OsDetector
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.util.GradleVersion
import org.moditect.commands.GenerateModuleInfo
import org.moditect.gradleplugin.common.ArtifactDescriptor
import org.moditect.gradleplugin.common.ModuleId
import org.moditect.gradleplugin.common.ModuleInfoConfiguration
import org.moditect.model.DependencePattern
import org.moditect.model.DependencyDescriptor
import org.moditect.model.GeneratedModuleInfo
import org.moditect.model.PackageNamePattern

import java.lang.module.ModuleFinder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@CompileStatic
class Util {
    private static final Logger LOGGER = Logging.getLogger(Util)

    static DirectoryProperty createDirectoryProperty(Project project) {
        if(GradleVersion.current() < GradleVersion.version('5.0-milestone-1')) {
            return project.layout.directoryProperty()
        } else {
            return project.objects.directoryProperty()
        }
    }
    static RegularFileProperty createRegularFileProperty(Project project) {
        if(GradleVersion.current() < GradleVersion.version('5.0-milestone-1')) {
            return project.layout.fileProperty()
        } else {
            return project.objects.fileProperty()
        }
    }

    static <T> void addToListProperty(ListProperty<T> listProp, T... values) {
        if(GradleVersion.current() < GradleVersion.version('5.0-milestone-1')) {
            def list = new ArrayList(listProp.get())
            list.addAll(values as List)
            listProp.set(list)
        } else {
            listProp.addAll(values as List)
        }
    }

    static boolean isEmptyJar(File jarFile) {
        def zipFile = new ZipFile(jarFile)
        zipFile.entries().every { ZipEntry entry -> entry.name in ['META-INF/', 'META-INF/MANIFEST.MF']}
    }

    static void createDirectory(DirectoryProperty dirProp) {
        if ( !dirProp.get().asFile.exists() ) {
            dirProp.get().asFile.mkdirs();
        }
    }

    static GeneratedModuleInfo generateModuleInfo(File workingDirectory, File outputDirectory, ModuleInfoConfiguration moduleInfo,
                                                  File inputJar, Set<DependencyDescriptor> dependencies, List<String> jdepsExtraArgs) {
        new GenerateModuleInfo(
                inputJar.toPath(),
                moduleInfo.name,
                moduleInfo.open,
                dependencies,
                PackageNamePattern.parsePatterns(moduleInfo.exports),
                PackageNamePattern.parsePatterns(moduleInfo.opens),
                DependencePattern.parsePatterns(moduleInfo.requires),
                workingDirectory.toPath(),
                outputDirectory.toPath(),
                moduleInfo.usesAsSet,
                moduleInfo.addServiceUses,
                jdepsExtraArgs,
                new ModitectLog()
        ).run()
    }

    static Set<DependencyDescriptor> getDependencyDescriptors(
            Collection<ArtifactDescriptor> artifactDescriptors,
            Map<ModuleId, String> assignedNamesByModule,
            Collection<ModuleId> optionalDependencies) {
        artifactDescriptors.collect { artifact ->
                def optional = optionalDependencies.any { it.name == artifact.name && it.group == artifact.group}
                def assignedModuleName = assignedNamesByModule.get(new ModuleId(group: artifact.group, name: artifact.name))
                return new DependencyDescriptor(artifact.file.toPath(), optional, assignedModuleName)
        } as Set
    }
    static Configuration getFullConfiguration(Project project) {
        project.configurations.getByName(ModitectPlugin.FULL_CONFIGURATION_NAME)
    }

    static Object getAdjustedDependencyNotation(Project project, Object dependencyNotation) {
        if(!(dependencyNotation instanceof String)) return dependencyNotation
        def tokens = (dependencyNotation as String).split(':')
        if(tokens.length != 2) return dependencyNotation
        def cfg = project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)?.copyRecursive()
        if(!cfg) return dependencyNotation
        cfg.extendsFrom(project.configurations.getByName(ModitectPlugin.FULL_CONFIGURATION_NAME).copyRecursive())
        cfg.canBeResolved = true
        cfg.resolve()
        String version = cfg.resolvedConfiguration.resolvedArtifacts*.moduleVersion*.id?.find { it.group == tokens[0] && it.name == tokens[1] }?.version
        if(!version) throw new GradleException("Cannot determine version of $dependencyNotation")
        LOGGER.info "Found version $version for $dependencyNotation"
        return "$dependencyNotation:$version"
    }

    static ModitectExtension getModitectExtension(Project project) {
        (ModitectExtension)project.extensions.getByName(ModitectPlugin.EXTENSION_NAME)
    }

    static String resolveClassifier(String classifier) {
        if(classifier && classifier.contains('${os.detected')) {
            def os = new OsDetector()
            classifier = classifier.replace('${os.detected.classifier}', os.classifier)
            classifier = classifier.replace('${os.detected.name}', os.os)
            classifier = classifier.replace('${os.detected.arch}', os.arch)
        }
        classifier
    }
}
