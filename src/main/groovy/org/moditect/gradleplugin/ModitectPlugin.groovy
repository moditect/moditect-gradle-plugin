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
package org.moditect.gradleplugin

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.moditect.gradleplugin.add.AddDependenciesModuleInfoTask
import org.moditect.gradleplugin.add.AddMainModuleInfoTask
import org.moditect.gradleplugin.generate.GenerateModuleInfoTask
import org.moditect.gradleplugin.image.CreateRuntimeImageTask

@CompileStatic
class ModitectPlugin implements Plugin<Project> {
    final static String EXTENSION_NAME = 'moditect'
    final static String CREATE_RUNTIME_TASK_NAME = 'createRuntimeImage'
    final static String GENERATE_MODULE_INFO_TASK_NAME = 'generateModuleInfo'
    final static String ADD_MAIN_MODULE_INFO_TASK_NAME = 'addMainModuleInfo'
    final static String ADD_DEPENDENCIES_MODULE_INFO_TASK_NAME = 'addDependenciesModuleInfo'
    final static String FULL_CONFIGURATION_NAME = 'moditectFull'

    @Override
    void apply(Project project) {
        project.configurations.maybeCreate(FULL_CONFIGURATION_NAME)
        def ext = project.extensions.create(EXTENSION_NAME, ModitectExtension, project)

        def addMainModuleInfoTask = project.tasks.create(ADD_MAIN_MODULE_INFO_TASK_NAME, AddMainModuleInfoTask)
        addMainModuleInfoTask.group = 'moditect'
        addMainModuleInfoTask.description = 'Adds a module descriptor to the project JAR'

        def addDependenciesModuleInfoTask = project.tasks.create(ADD_DEPENDENCIES_MODULE_INFO_TASK_NAME, AddDependenciesModuleInfoTask)
        addDependenciesModuleInfoTask.group = 'moditect'
        addDependenciesModuleInfoTask.description = 'Adds module descriptors to existing JAR files'

        def generateModuleInfoTask = project.tasks.create(GENERATE_MODULE_INFO_TASK_NAME, GenerateModuleInfoTask)
        generateModuleInfoTask.group = 'moditect'
        generateModuleInfoTask.description = 'Generates module descriptors'

        def createRuntimeImageTask = project.tasks.create(CREATE_RUNTIME_TASK_NAME, CreateRuntimeImageTask)
        createRuntimeImageTask.group = 'moditect'
        createRuntimeImageTask.description = 'Creates a custom runtime image'

        ext.init(
                addMainModuleInfoTask,
                addDependenciesModuleInfoTask,
                generateModuleInfoTask,
                createRuntimeImageTask
        )

        // Configure the following execution order:
        // generateModuleInfo -> addDependenciesModuleInfo -> compileJava -> jar -> addMainModuleInfo -> dist-tasks -> createRuntimeImage

        def jarTask = project.tasks.findByName(JavaPlugin.JAR_TASK_NAME)
        def compileJavaTask = project.tasks.findByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)

        addDependenciesModuleInfoTask.dependsOn(generateModuleInfoTask)
        compileJavaTask.dependsOn(addDependenciesModuleInfoTask)

        addMainModuleInfoTask.dependsOn(jarTask)
        jarTask.finalizedBy(addMainModuleInfoTask)

        ['installDist', 'distZip', 'distTar', 'startScripts']
                .collect { project.tasks.findByName(it) }
                .findAll { it != null }
                .each { it.dependsOn addMainModuleInfoTask }

        createRuntimeImageTask.dependsOn(addMainModuleInfoTask)
    }
}
