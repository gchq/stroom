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

package stroom.datagen.impl;

import stroom.analytics.impl.ScheduledExecutable;
import stroom.analytics.impl.ScheduledExecutorService.ExecutionResult;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionTracker;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.meta.api.MetaProperties;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ScheduledDataGenExecutable implements ScheduledExecutable<DataGenDoc> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledDataGenExecutable.class);

    private final DataGenStore dataGenStore;
    private final Store streamStore;

    @Inject
    ScheduledDataGenExecutable(final DataGenStore dataGenStore,
                               final Store streamStore) {
        this.dataGenStore = dataGenStore;
        this.streamStore = streamStore;
    }

    @Override
    public ExecutionResult run(final DataGenDoc doc,
                               final Trigger trigger,
                               final Instant executionTime,
                               final Instant effectiveExecutionTime,
                               final ExecutionSchedule executionSchedule,
                               final ExecutionTracker currentTracker,
                               final ExecutionResult executionResult) {

        try {
            final DocRef outputFeed = doc.getFeed();

            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(outputFeed.getName())
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .effectiveMs(effectiveExecutionTime.toEpochMilli())
                    .build();

            try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                try (final OutputStreamProvider outputStreamProvider = streamTarget.next()) {
                    outputStreamProvider.get().write(doc.getTemplate().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return executionResult;
    }

    @Override
    public DocRef getDocRef(final DataGenDoc doc) {
        return doc.asDocRef();
    }

    @Override
    public DataGenDoc reload(final DataGenDoc doc) {
        return dataGenStore.readDocument(doc.asDocRef());
    }

    @Override
    public List<DataGenDoc> getDocs() {
        // TODO this is not very efficient. It fetches all the docrefs from the DB,
        //  then loops over them to fetch+deser the associated doc for each one (one by one)
        //  so the caller can filter half of them out by type.
        //  It would be better if we had a json type col in the doc table, so that the
        //  we can pass some kind of json path query to the persistence layer that the DBPersistence
        //  can translate to a MySQL json path query.
        final List<DataGenDoc> currentDataGenerators = new ArrayList<>();
        final List<DocRef> docRefs = dataGenStore.list();
        for (final DocRef docRef : docRefs) {
            try {
                final DataGenDoc dataGenDoc = dataGenStore.readDocument(docRef);
                if (dataGenDoc != null) {
                    currentDataGenerators.add(dataGenDoc);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return currentDataGenerators;
    }

    @Override
    public String getProcessType() {
        return "data gen";
    }

    @Override
    public String getIdentity(final DataGenDoc doc) {
        return NullSafe.get(doc, d -> d.getName() + " (" + d.getUuid() + ")");
    }
}
