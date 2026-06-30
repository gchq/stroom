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

package stroom.planb.impl.db;

import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBNameValidator;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Per-stream cache of resolved {@link PlanBDocument} instances.
 *
 * <p>Maintains two maps for the lifetime of a single stream execution:
 * <ul>
 *   <li>{@code resolvedDocs} — names that were successfully resolved; returned
 *       on subsequent lookups without hitting the underlying cache.</li>
 *   <li>{@code unresolvedNames} — names confirmed unresolvable (bad format or
 *       doc not found); permanently skipped for the duration of the stream.</li>
 * </ul>
 * Transient failures (e.g. cache miss during warm-up) are <em>not</em> added to
 * either map so that they are retried on the next row.
 *
 * <p><strong>Threading:</strong> not thread-safe; must be used from a single thread.
 */
public class PlanBDocumentResolver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBDocumentResolver.class);

    private final PlanBDocCache planBDocCache;
    private final Map<String, PlanBDocument> resolvedDocs = new HashMap<>();
    private final Set<String> unresolvedNames = new HashSet<>();

    public PlanBDocumentResolver(final PlanBDocCache planBDocCache) {
        this.planBDocCache = Objects.requireNonNull(planBDocCache, "planBDocCache must not be null");
    }

    /**
     * Resolves a {@link PlanBDocument} for the given map name.
     *
     * @param mapName       the pipeline map name to resolve; may be null or blank
     * @param errorConsumer called with a human-readable message for each
     *                      validation or lookup failure; must not be null
     * @return the resolved document, or {@link Optional#empty()} on any failure
     */
    public Optional<PlanBDocument> resolve(final String mapName,
                                           final Consumer<String> errorConsumer) {
        Objects.requireNonNull(errorConsumer, "errorConsumer must not be null");

        if (NullSafe.isBlankString(mapName)) {
            errorConsumer.accept("Map name is null or blank");
            return Optional.empty();
        }

        final PlanBDocument cached = resolvedDocs.get(mapName);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Skip names already confirmed as unresolvable (bad format, doc not found).
        // Transient failures (e.g. cache miss during warm-up) are NOT added here,
        // so they will be retried on the next row.
        if (unresolvedNames.contains(mapName)) {
            return Optional.empty();
        }

        // First time seeing this name: validate and look it up.
        try {
            if (!PlanBNameValidator.isValidName(mapName)) {
                errorConsumer.accept("Bad map name: " + mapName);
                unresolvedNames.add(mapName);
                return Optional.empty();
            }
            final PlanBDocument doc = planBDocCache.get(mapName);
            if (doc == null) {
                errorConsumer.accept("Unable to find state doc for map name: " + mapName);
                unresolvedNames.add(mapName);
                return Optional.empty();
            }
            resolvedDocs.put(mapName, doc);
            return Optional.of(doc);
        } catch (final RuntimeException e) {
            // Do not add to unresolvedNames — transient failures should be retried.
            LOGGER.debug(e::getMessage, e);
            errorConsumer.accept(e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getSimpleName() + " (no message)");
            return Optional.empty();
        }
    }
}
