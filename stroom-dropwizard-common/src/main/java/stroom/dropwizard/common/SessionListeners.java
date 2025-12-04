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

package stroom.dropwizard.common;

import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Set;

public class SessionListeners {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionListeners.class);

    private final Environment environment;
    private final Set<HttpSessionListener> sessionListeners;
    private final Set<HttpSessionIdListener> sessionIdListeners;

    @Inject
    SessionListeners(final Environment environment,
                     final Set<HttpSessionListener> sessionListeners,
                     final Set<HttpSessionIdListener> sessionIdListeners) {
        this.environment = environment;
        this.sessionListeners = sessionListeners;
        this.sessionIdListeners = sessionIdListeners;
    }

    public void register() {
        LOGGER.info("Adding session listeners:");
        sessionListeners.stream()
                .sorted(Comparator.comparing(sessionListener -> sessionListener.getClass().getName()))
                .forEach(sessionListener -> {
                    final String name = sessionListener.getClass().getName();
                    LOGGER.info("\t{}", name);
                    environment.servlets().addServletListeners(sessionListener);
                });

        LOGGER.info("Adding session Id listeners:");
        sessionIdListeners.stream()
                .sorted(Comparator.comparing(sessionListener -> sessionListener.getClass().getName()))
                .forEach(sessionIdListener -> {
                    final String name = sessionIdListener.getClass().getName();
                    LOGGER.info("\t{}", name);
                    environment.servlets().addServletListeners(sessionIdListener);
                });
    }
}
