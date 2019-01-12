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
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.moditect.gradleplugin.Util
import org.moditect.gradleplugin.add.model.AbstractModuleConfiguration
import org.moditect.model.GeneratedModuleInfo

import static org.moditect.gradleplugin.Util.createDirectoryProperty

@CompileStatic
@CacheableTask
abstract class AbstractAddModuleInfoTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(AbstractAddModuleInfoTask)

    @OutputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty workingDirectory

    @OutputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty outputDirectory

    @Internal
    final DirectoryProperty tmpDirectory

    @Input @Optional
    final ListProperty<String> jdepsExtraArgs

    @Input @Optional
    final Property<String> jvmVersion

    @Input
    final Property<Boolean> overwriteExistingFiles

    AbstractAddModuleInfoTask() {
        workingDirectory = createDirectoryProperty(project)

        outputDirectory = createDirectoryProperty(project)
        outputDirectory.set(project.file("$project.buildDir/modules"))

        tmpDirectory = createDirectoryProperty(project)
        tmpDirectory.set(project.file("$project.buildDir/tmp-sources/modules"))

        jdepsExtraArgs = project.objects.listProperty(String)

        jvmVersion = project.objects.property(String)

        overwriteExistingFiles = project.objects.property(Boolean)
        overwriteExistingFiles.set(false)
    }

    protected String getModuleInfoSource(AbstractModuleConfiguration moduleCfg) {
        def artifactsInfo  = Util.getModitectExtension(project).artifactsInfo
        if ( moduleCfg.moduleInfo) {
            LOGGER.info("Generating module-info.java for $moduleCfg.shortName")
            GeneratedModuleInfo generatedModuleInfo = Util.generateModuleInfo(
                    workingDirectory.get().asFile,
                    tmpDirectory.get().asFile,
                    moduleCfg.moduleInfo,
                    moduleCfg.inputJar,
                    Util.getDependencyDescriptors(
                            artifactsInfo.fullFixedArtifactDescriptors,
                            artifactsInfo.assignedNamesByModules,
                            moduleCfg.optionalDependencies
                    ),
                    jdepsExtraArgs.get() as List<String>
            )
            return generatedModuleInfo.path.text
        } else if (moduleCfg.moduleInfoSource) {
            return moduleCfg.moduleInfoSource;
        } else if (moduleCfg.moduleInfoFile) {
            return moduleCfg.moduleInfoFile.text;
        } else {
            throw new GradleException( "Either 'moduleInfo' or 'moduleInfoFile' or 'moduleInfoSource' must be specified for $moduleCfg.shortName." );
        }
    }
}
