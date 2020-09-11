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
package org.moditect.gradleplugin.common

import groovy.transform.Canonical
import groovy.transform.ToString

import java.util.function.Supplier

@Canonical
@ToString(excludes = ['file', 'fileProvider'])
class ArtifactDescriptor {
    final String group
    final String name
    final Supplier<File> fileProvider
    @Lazy volatile File file = { fileProvider.get() }()

    ArtifactDescriptor(String group, String name, Supplier<File> fileProvider) {
        this.group = group
        this.name = name
        this.fileProvider = fileProvider
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        ArtifactDescriptor that = (ArtifactDescriptor) o
        if (group != that.group) return false
        if (name != that.name) return false
        return true
    }

    int hashCode() {
        int result
        result = (group != null ? group.hashCode() : 0)
        result = 31 * result + (name != null ? name.hashCode() : 0)
        return result
    }
}
