/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.startup;

import org.eclipse.jetty.server.session.SessionHandler;
import stroom.security.spring.SecurityConfiguration;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.thread.ThreadScopeContextHolder;

/**
 * Configures  the environment, including the Dropwizard Environment as well as system properties and misc.
 */
public class Environment {
    public static void configure(io.dropwizard.setup.Environment environment) {
        // Set up a session manager for Jetty
        SessionHandler sessions = new SessionHandler();
        environment.servlets().setSessionHandler(sessions);

        // We want Stroom to use the root path so we need to move Dropwizard's path.
        environment.jersey().setUrlPattern("/api/*");

        // We need to set this otherwise we won't have all the beans we need.
        System.setProperty("spring.profiles.active", String.format("%s,%s", StroomSpringProfiles.PROD, SecurityConfiguration.PROD_SECURITY));

        // We need to prime this otherwise we won't have a thread scope context and bean initialisation will fail
        ThreadScopeContextHolder.createContext();
    }
}
