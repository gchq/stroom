/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

public final class LambdaLoggerFactory {

    private LambdaLoggerFactory() {
        // Factory.
    }

    public static LambdaLogger getLogger(final Class<?> clazz) {
        final Logger logger = LoggerFactory.getLogger(clazz.getName());

        if (logger instanceof LocationAwareLogger) {
            return new LocationAwareLambdaLogger((LocationAwareLogger) logger);
        }

        return new BasicLambdaLogger(logger);
    }
}
