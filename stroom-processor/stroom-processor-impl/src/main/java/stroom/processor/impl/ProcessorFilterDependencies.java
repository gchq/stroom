/*
 * Copyright 2026 Crown Copyright
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

package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.meta.shared.MetaFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.common.v2.LegacyDocRefConverter;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Set;

/// Keeps the stored dependency edges of processor filters current.
///
/// Processor filters are not documents, so they have no `doc` row and are not covered by the document
/// dependency rebuild. Instead each filter records the version of the extraction logic that wrote its
/// edges in its own `processor_filter.dependency_version` column, and
/// [ProcessorFilterDependencyRebuildStartup] rebuilds any filter whose edges are stale.
///
/// A logically deleted filter has no edges.
@Singleton
public class ProcessorFilterDependencies {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterDependencies.class);

    /// The version of the processor filter dependency extraction logic. Each filter's
    /// `dependency_version` column is stamped with this when its edges are written. Increment it when a
    /// change to [#getDependencies(ProcessorFilter)] means existing edges need rebuilding: every filter
    /// is then rebuilt once by [ProcessorFilterDependencyRebuildStartup] on the next start.
    public static final int CURRENT_DEPENDENCY_VERSION = 1;

    private final ProcessorFilterDao processorFilterDao;
    private final DocFinder docFinder;
    private final SecurityContext securityContext;
    private final Provider<DocDependencyService> docDependencyServiceProvider;

    @Inject
    ProcessorFilterDependencies(final ProcessorFilterDao processorFilterDao,
                                final DocFinder docFinder,
                                final SecurityContext securityContext,
                                final Provider<DocDependencyService> docDependencyServiceProvider) {
        this.processorFilterDao = processorFilterDao;
        this.docFinder = docFinder;
        this.securityContext = securityContext;
        this.docDependencyServiceProvider = docDependencyServiceProvider;
    }

    /// Replace the stored dependency edges of a filter with those extracted from the supplied filter,
    /// then stamp the filter with [#CURRENT_DEPENDENCY_VERSION].
    ///
    /// Extracts directly from the in-hand object rather than re-reading it. Runs as the processing user
    /// as resolving the pipeline name may need privileged lookups. Failures writing the edges are logged
    /// by [DocDependencyService] so they cannot break the filter write.
    ///
    /// @param processorFilter The filter as just persisted, so its ID and version are current.
    /// @return `true` if the filter was stamped, `false` if it has been changed since it was read, in
    /// which case the edges may be out of date.
    public boolean update(final ProcessorFilter processorFilter) {
        final String uuid = NullSafe.get(processorFilter, ProcessorFilter::getUuid);
        if (uuid == null) {
            return false;
        }
        return securityContext.asProcessingUserResult(() -> {
            final Set<DocRef> dependencies = getDependencies(processorFilter);
            docDependencyServiceProvider.get().setDependencies(createDocRef(uuid), dependencies);
            return stamp(processorFilter);
        });
    }

    /// Remove all the stored dependency edges of a filter, e.g. because it has been deleted.
    ///
    /// @param uuid The UUID of the filter. Ignored if null.
    public void remove(final String uuid) {
        if (uuid != null) {
            securityContext.asProcessingUser(() ->
                    docDependencyServiceProvider.get().removeDependencies(createDocRef(uuid)));
        }
    }

    /// Remove all the stored dependency edges of several filters.
    ///
    /// @param uuids The UUIDs of the filters. Ignored if null or empty.
    public void remove(final Collection<String> uuids) {
        NullSafe.forEach(uuids, this::remove);
    }

    /// Remove all the stored dependency edges of a filter that is logically deleted, then stamp the
    /// filter with [#CURRENT_DEPENDENCY_VERSION] so it is not rebuilt again.
    ///
    /// @param processorFilter The deleted filter.
    /// @return `true` if the filter was stamped, `false` if it has been changed since it was read.
    public boolean removeForDeleted(final ProcessorFilter processorFilter) {
        remove(processorFilter.getUuid());
        return stamp(processorFilter);
    }

    /// Extract the documents a filter depends on: its pipeline (or analytic rule for a streaming analytic
    /// filter), the documents referenced in its expression (e.g. feeds and dictionaries), and its data
    /// source unless that is the stream store.
    ///
    /// Almost every filter reads from the stream store, which is not a document, so a dependency on it
    /// adds nothing. A filter created from a dashboard search reads from the dashboard's data source,
    /// e.g. an index, which is a document the filter cannot run without, so that is kept.
    ///
    /// @param processorFilter The filter.
    /// @return The filter's dependencies.
    Set<DocRef> getDependencies(final ProcessorFilter processorFilter) {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();
        final QueryData queryData = processorFilter.getQueryData();
        if (queryData != null) {
            // Older filters hold the stream store under legacy refs, e.g. a uuid of '0'
            final DocRef dataSource = LegacyDocRefConverter.convert(queryData.getDataSource());
            if (dataSource != null && !MetaFields.STREAM_STORE_TYPE.equals(dataSource.getType())) {
                dependencyRemapper.remap(dataSource);
            }
            if (queryData.getExpression() != null) {
                dependencyRemapper.remapExpression(queryData.getExpression());
            }
        }
        if (NullSafe.isNonBlankString(processorFilter.getPipelineUuid())) {
            // For a streaming analytic filter this is the analytic rule rather than a pipeline
            final DocRef pipeline = processorFilter.getPipeline();
            final String name = docFinder.getName(pipeline).orElse("Unknown");
            dependencyRemapper.remap(new DocRef(pipeline.getType(), pipeline.getUuid(), name));
        }
        return dependencyRemapper.getDependencies();
    }

    private boolean stamp(final ProcessorFilter processorFilter) {
        final Integer id = processorFilter.getId();
        final Integer version = processorFilter.getVersion();
        if (id == null || version == null) {
            LOGGER.debug("Unable to stamp dependency version on filter with no id or version {}",
                    processorFilter.getUuid());
            return false;
        }
        return processorFilterDao.setDependencyVersion(id, version, CURRENT_DEPENDENCY_VERSION);
    }

    private static DocRef createDocRef(final String uuid) {
        return new DocRef(ProcessorFilter.ENTITY_TYPE, uuid);
    }
}
