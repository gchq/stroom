/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.dao;

import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocNotFoundException;
import stroom.planb.shared.PlanBDocument;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A map name that does not exist must be reported once per stream, not once per record.
 *
 * <p>A missing name is permanent for the life of a stream, so retrying it turns one configuration mistake
 * into one error per row — millions of them on a large stream.
 */
class TestPlanBDocumentResolver {

    // Must satisfy PlanBNameValidator's ^[a-z_0-9]+$ or the lookup is rejected before the cache is reached.
    private static final String MISSING = "no_such_map";

    @Test
    void missingMapNameIsReportedOnce_howeverManyRowsAskForIt() {
        final AtomicInteger lookups = new AtomicInteger();
        final List<String> errors = new ArrayList<>();
        final PlanBDocumentResolver resolver = new PlanBDocumentResolver(
                notFound(() -> lookups.incrementAndGet()));

        for (int row = 0; row < 100; row++) {
            assertThat(resolver.resolve(MISSING, errors::add)).isEmpty();
        }

        assertThat(errors).as("one error for the stream, not one per row").hasSize(1);
        assertThat(errors.getFirst()).contains(MISSING);
        assertThat(lookups.get()).as("and the cache is asked only once").isEqualTo(1);
    }

    /** The contrast: a failure that might not be permanent must keep being retried. */
    @Test
    void transientFailureIsRetriedOnEveryRow() {
        final List<String> errors = new ArrayList<>();
        final PlanBDocumentResolver resolver = new PlanBDocumentResolver(
                throwing(() -> new IllegalStateException("cache warming up")));

        for (int row = 0; row < 5; row++) {
            assertThat(resolver.resolve(MISSING, errors::add)).isEmpty();
        }

        assertThat(errors).as("retried, so reported each time").hasSize(5);
    }

    private static PlanBDocCache notFound(final Runnable onLookup) {
        return throwing(() -> {
            onLookup.run();
            return new PlanBDocNotFoundException(MISSING);
        });
    }

    private static PlanBDocCache throwing(final Supplier<RuntimeException> failure) {
        return new PlanBDocCache() {
            @Override
            public List<PlanBDocument> getAll() {
                return List.of();
            }

            @Override
            public PlanBDocument get(final String name) {
                throw failure.get();
            }

            @Override
            public void remove(final String name) {
            }
        };
    }
}
