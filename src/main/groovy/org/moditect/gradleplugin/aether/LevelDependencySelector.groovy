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
package org.moditect.gradleplugin.aether

import groovy.transform.CompileStatic
import org.eclipse.aether.collection.DependencyCollectionContext
import org.eclipse.aether.collection.DependencySelector
import org.eclipse.aether.graph.Dependency

@CompileStatic
final class LevelDependencySelector implements DependencySelector {
    private final int maxDepth
    private final int depth

    LevelDependencySelector(int maxDepth) {
        this.maxDepth = maxDepth
        depth = 0
    }

    private LevelDependencySelector(int maxDepth, int depth) {
        this.maxDepth = maxDepth
        this.depth = depth
    }

    boolean selectDependency(Dependency dependency ) {
        return depth < maxDepth
    }

    DependencySelector deriveChildSelector(DependencyCollectionContext context ) {
        return (depth < maxDepth) ? new LevelDependencySelector(maxDepth, depth + 1 ) : this
    }

    @Override
    boolean equals(Object obj ) {
        if ( this.is(obj) ) return true
        if ( !obj || !getClass().equals( obj.getClass() ) ) return false
        return depth == ((LevelDependencySelector) obj).depth
    }

    @Override
    int hashCode() {
        31 * getClass().hashCode() + depth
    }
}
