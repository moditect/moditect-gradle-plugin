/*
 * Copyright 2019 The ModiTect authors.
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
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.moditect.gradleplugin.common.ModuleId

@CompileStatic
@ToString(includeNames = true, includeSuperProperties = true)
class MainModuleConfiguration extends AbstractModuleConfiguration implements Serializable {
    MainModuleConfiguration(Project project) {
        super(project)
    }

    @Override
    String getShortName() {
        'mainModule'
    }

    @Override
    File getInputJar() {
        Jar jarTask =  (Jar)project.tasks.findByName(JavaPlugin.JAR_TASK_NAME)
        jarTask.archiveFile.get().asFile
    }

    @Override
    String getVersion() {
        project.getVersion() as String
    }

    @Override
    Set<ModuleId> getOptionalDependencies() {
        [] as Set
    }
}
