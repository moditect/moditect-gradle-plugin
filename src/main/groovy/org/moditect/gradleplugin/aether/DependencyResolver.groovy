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
package org.moditect.gradleplugin.aether

import groovy.transform.CompileStatic
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.graph.selector.AndDependencySelector
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

@CompileStatic
class DependencyResolver {
    private static final Logger LOGGER = Logging.getLogger(DependencyResolver)

    private final Project project

    private final RepositorySystem repoSystem = newRepositorySystem()
    private final RepositorySystemSession session  = newSession( repoSystem )
    @Lazy private volatile List<RemoteRepository> repositories = { createRepos() }()

    DependencyResolver(Project project) {
        this.project = project
    }

    Set<Dependency> getDependencies(String groupId, String artifactId, String version) throws Exception {
        def artifact = new DefaultArtifact(groupId, artifactId, 'jar', version)
        Dependency dependency = new Dependency(artifact, "compile");

        CollectRequest collectRequest = new CollectRequest()
        collectRequest.root = dependency
        collectRequest.repositories = repositories

        DependencyNode node = repoSystem.collectDependencies( session, collectRequest ).getRoot();
        Set<Dependency> deps = collectDependencies(node);
        LOGGER.info "aether dependencies($groupId:$artifactId:$version):\n${deps.join('\n\t')}"
        deps
    }

    Set<Dependency> getDependencies(org.gradle.api.artifacts.Dependency dep) {
        getDependencies(dep.group, dep.name, dep.version)
    }


    private List<RemoteRepository> createRepos() {
        List<RemoteRepository> repos = []
        project.repositories.findAll{it instanceof MavenArtifactRepository}.each { repo ->
            def mavenRepo = (MavenArtifactRepository )repo
            LOGGER.info "$mavenRepo.name ($mavenRepo.url): $mavenRepo.artifactUrls"
            repos << new RemoteRepository.Builder( mavenRepo.name, 'default', mavenRepo.url.toString() ).build()
        }
        repos
    }

    private static Set<Dependency> collectDependencies(DependencyNode node) {
        Set<Dependency> deps = []
        node.children.each { updateDependencies(deps, it, node) }
        deps
    }

    static void updateDependencies(Set<Dependency> dependencies, DependencyNode node, DependencyNode parent) {
        dependencies.add(node.getDependency());
        node.children.each { updateDependencies(dependencies, it, node) }
    }

    private static RepositorySystem newRepositorySystem() {
        def locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService( RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory )
        locator.addService( TransporterFactory.class, FileTransporterFactory )
        locator.addService( TransporterFactory.class, HttpTransporterFactory )
        return locator.getService( RepositorySystem )
    }

    private static RepositorySystemSession newSession(RepositorySystem system ) {
        def session = MavenRepositorySystemUtils.newSession();

        def localRepo = new LocalRepository( "build/local-repo" );
        session.localRepositoryManager = system.newLocalRepositoryManager( session, localRepo )
        session.dependencySelector = new AndDependencySelector(
                new ScopeDependencySelector(['compile', 'provided'], ['test', 'system']),
                new LevelDependencySelector(2),
                new ExclusionDependencySelector()
        )
        session
    }
}
