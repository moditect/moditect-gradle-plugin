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
package org.moditect.gradleplugin

import groovy.transform.CompileStatic
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.moditect.spi.log.Log

@CompileStatic
class ModitectLog implements Log {
    private static final Logger LOGGER = Logging.getLogger(ModitectLog)

    @Override void debug(CharSequence message) { LOGGER.info(message as String) }
    @Override void info(CharSequence message) { LOGGER.info(message as String) }
    @Override void warn(CharSequence message) { LOGGER.warn(message as String) }
    @Override void error(CharSequence message) { LOGGER.error(message as String) }
}
