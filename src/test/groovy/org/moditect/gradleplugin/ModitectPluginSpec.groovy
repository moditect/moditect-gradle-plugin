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

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.OperatingSystem

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class ModitectPluginSpec extends Specification {
    private static final Logger LOGGER = Logging.getLogger(ModitectPluginSpec)

    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()

    long helloServerStartWaitSeconds = (System.getenv('HELLO_SERVER_START_WAIT_SECONDS') ?: '20') as long
    int port = (System.getenv('HELLO_SERVER_PORT') ?: '8080') as int

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
        given:
        Process process = null

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

        when:
        process = imageLauncher.absolutePath.execute([], imageBinDir)
        def appendable = waitUntilOutputContains('HelloWorldServer successfully started',
                process, helloServerStartWaitSeconds, TimeUnit.SECONDS)
        LOGGER.info "process output:\n$appendable.delegate"

        then:
        appendable.containsExpectedText()

        when:
        def response = "http://localhost:$port?name=moditect".toURL().text

        then:
        assert response == 'Hello, moditect!'

        cleanup:
        if(process) {
            killRecursively(process.toHandle())
        }

        where:
        projectName | imageDir      | launcherName
        'undertow'  | 'image'       | 'helloWorld'
        'vert.x'    | 'jlink-image' | 'helloWorld'
    }



    private static class SignallingAppendable implements Appendable{
        final StringBuilder delegate = new StringBuilder(2048)
        final String expectedText
        final Lock lock
        final Condition textDetected

        SignallingAppendable(expectedText, Lock lock, Condition textDetected) {
            this.expectedText = expectedText
            this.lock = lock
            this.textDetected = textDetected
        }

        @Override
        Appendable append(CharSequence csq) throws IOException {
            delegate.append(csq)
            return this
        }

        @Override
        Appendable append(CharSequence csq, int start, int end) throws IOException {
            delegate.append(csq, start, end)
            return this
        }

        @Override
        Appendable append(char c) throws IOException {
            delegate.append(c)
            return this
        }

        void checkContent() {
            if(containsExpectedText()) {
                lock.lock()
                try {
                    textDetected.signalAll()
                } finally {
                    lock.unlock()
                }
            }
        }

        boolean containsExpectedText() {
            delegate.contains(expectedText)
        }
    }

    private static SignallingAppendable waitUntilOutputContains(String expectedText, Process process, long delay, TimeUnit timeUnit) {
        def lock = new ReentrantLock()
        def textDetected = lock.newCondition()
        def appendable = new SignallingAppendable(expectedText, lock, textDetected)
        lock.lock()
        try {
            process.consumeProcessOutput(appendable, appendable)
            while(!appendable.containsExpectedText()) {
                textDetected.await(delay, timeUnit)
            }
        } finally {
            lock.unlock()
        }
        appendable
    }

    private static void killRecursively(ProcessHandle processHandle) {
        processHandle.descendants().forEach{
            killRecursively(it)
        }
        boolean terminated = processHandle.destroyForcibly()
        LOGGER.info "Process $processHandle terminated: $terminated"
    }
}
