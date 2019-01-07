/*
 * Copyright 2019 the original author or authors.
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
import groovy.transform.ToString
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.moditect.gradleplugin.common.ModuleId
import org.moditect.gradleplugin.common.ModuleInfoConfiguration

import static org.gradle.util.ConfigureUtil.configure

@CompileStatic
@ToString(includeNames = true)
abstract class AbstractModuleConfiguration implements Serializable {
    transient protected final Project project

    ModuleInfoConfiguration moduleInfo
    File moduleInfoFile
    String moduleInfoSource
    String mainClass

    abstract String getShortName()
    abstract File getInputJar()
    abstract String getVersion()
    abstract Set<ModuleId> getOptionalDependencies()

    AbstractModuleConfiguration(Project project) {
        this.project = project
    }

    void moduleInfo(Closure configureClosure) {
        if(moduleInfo) throw new GradleException("Multiple moduleInfo() calls in $shortName")
        moduleInfo = new ModuleInfoConfiguration()
        configure(configureClosure, moduleInfo)
    }

    void checkModuleInfo() {
        if([moduleInfo, moduleInfoSource, moduleInfoFile].count { it != null } != 1) {
            throw new GradleException("Exactly one of 'moduleInfo', 'moduleInfoSource', or 'moduleInfoFile' must be specified in $shortName")
        }
    }
}
