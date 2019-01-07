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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.OperatingSystem

class ModitectPluginSpec extends Specification {
    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()

    def setUpBuild(String projectDir) {
        new AntBuilder().copy(todir: testProjectDir.root) {
            fileset(dir: "integrationtest//$projectDir")
        }
    }

    @Unroll
    def "should create and execute runtime image of #projectName"() {
        when:
        setUpBuild(projectName)

        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments(ModitectPlugin.CREATE_RUNTIME_TASK_NAME, '-is')
                .build();
        def imageBinDir = new File(testProjectDir.root, 'build/image/bin')
        def launcherExt = OperatingSystem.current.windows ? '.bat' : ''
        def imageLauncher = new File(imageBinDir, "$launcherName$launcherExt")

        then:
        result.task(":$ModitectPlugin.CREATE_RUNTIME_TASK_NAME").outcome == TaskOutcome.SUCCESS
        imageLauncher.exists()
        imageLauncher.canExecute()

        when:
        def process = imageLauncher.absolutePath.execute([], imageBinDir)
        def out = new ByteArrayOutputStream(2048)
        process.waitForProcessOutput(out, out)
        def outputText = out.toString()

        then:
        outputText.contains(expectedContent)

        where:
        projectName           | launcherName     | expectedContent
        'hibernate-validator' | 'validationTest' | 'javax.validation.constraints.NotNull.message'
    }
    
    @Unroll
    def "should create runtime image of #projectName"() {
        when:
        setUpBuild(projectName)

        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments(ModitectPlugin.CREATE_RUNTIME_TASK_NAME, '-is')
                .build();
        def imageBinDir = new File(testProjectDir.root, "build/$imageDir/bin")
        def launcherExt = OperatingSystem.current.windows ? '.bat' : ''
        def imageLauncher = new File(imageBinDir, "$launcherName$launcherExt")

        then:
        result.task(":$ModitectPlugin.CREATE_RUNTIME_TASK_NAME").outcome == TaskOutcome.SUCCESS
        imageLauncher.exists()
        imageLauncher.canExecute()

        where:
        projectName | imageDir      | launcherName
        'undertow'  | 'image'       | 'helloWorld'
        'vert.x'    | 'jlink-image' | 'helloWorld'
    }

}
