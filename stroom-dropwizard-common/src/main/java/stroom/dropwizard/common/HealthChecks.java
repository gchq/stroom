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

import stroom.util.HasHealthCheck;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Set;

public class HealthChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthChecks.class);

    private final Environment environment;
    private final Set<HasHealthCheck> healthChecks;

    @Inject
    HealthChecks(final Environment environment,
                 final Set<HasHealthCheck> healthChecks) {
        this.environment = environment;
        this.healthChecks = healthChecks;
    }

    public void register() {
        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();
        LOGGER.info("Adding health checks:");
        healthChecks.stream()
                .sorted(Comparator.comparing(hasHealthCheck ->
                        hasHealthCheck.getClass().getSimpleName()))
                .forEach(hasHealthCheck -> {
                    final String name = hasHealthCheck.getClass().getName();
                    LOGGER.info("\t{}", name);
                    healthCheckRegistry.register(name, hasHealthCheck.getHealthCheck());
                });
    }
}
