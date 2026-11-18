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

package stroom.ai.impl.workspace;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/// Decides whether the current user may still use what a workspace file holds, from the document its content came
/// from (its source). A data source (anything a search can run on, e.g. an index or a view) needs Use, as Use is
/// what lets someone search it; any other document (e.g. an XSLT whose text a tool read) needs View, as Use does
/// not let someone read it. A source whose type can't be found, e.g. because it has been deleted, can't be used.
///
/// Each source's type is looked up by its UUID and kept for a short while, as a file can be checked many times in a
/// run. Only the type is kept, never whether a user may use it, which is checked every time. A check that can't be
/// made (e.g. a lookup fails for a moment) says so, rather than that access is denied, so a file isn't removed for a
/// passing fault.
@Singleton
public class SourceDocAccess {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceDocAccess.class);

    /// How long a source's type is kept.
    static final Duration TYPE_CACHE_TIME = Duration.ofMinutes(1);
    /// The most source types kept at once.
    static final int MAX_CACHED_TYPES = 1_000;

    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider;
    private final SecurityContext securityContext;
    private final LongSupplier clock;
    private final Map<String, CachedType> types = new ConcurrentHashMap<>();
    private volatile Set<String> dataSourceTypes;

    /// @param explorerNodeServiceProvider        Finds a source's type in the Explorer tree.
    /// @param dataSourceProviderRegistryProvider Lists the data source types, and finds data sources that aren't in
    ///                                           the tree.
    /// @param securityContext                    Checks the user's permissions.
    @Inject
    SourceDocAccess(final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                    final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider,
                    final SecurityContext securityContext) {
        this(explorerNodeServiceProvider, dataSourceProviderRegistryProvider, securityContext,
                System::currentTimeMillis);
    }

    /// As [#SourceDocAccess(Provider, Provider, SecurityContext)], with a clock, for tests.
    ///
    /// @param clock The time now, in milliseconds.
    SourceDocAccess(final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                    final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider,
                    final SecurityContext securityContext,
                    final LongSupplier clock) {
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.dataSourceProviderRegistryProvider = dataSourceProviderRegistryProvider;
        this.securityContext = securityContext;
        this.clock = clock;
    }

    /// @param sourceDocUuid The UUID of the document a file's content came from.
    /// @return Whether the current user may still use what came from it (Use on a data source, View on any other
    /// document): [Access#ALLOWED]; [Access#DENIED] if they may not, or it no longer exists; or
    /// [Access#UNKNOWN] if that couldn't be checked, e.g. as a lookup failed.
    public Access access(final String sourceDocUuid) {
        return access(sourceDocUuid, false);
    }

    /// As [#access(String)], for a file that may hold the document's own content rather than data searched from it.
    ///
    /// @param sourceDocUuid   The UUID of the document a file's content came from.
    /// @param documentContent True if the file holds the document's own content (e.g. its text, or a snapshot of
    ///                        its tab), which needs View even for a data source (e.g. an index's settings), as Use
    ///                        only lets someone search it.
    /// @return Whether the current user may still use the file's content.
    public Access access(final String sourceDocUuid, final boolean documentContent) {
        if (sourceDocUuid == null || sourceDocUuid.isBlank()) {
            return Access.DENIED;
        }
        final Optional<String> type;
        try {
            type = type(sourceDocUuid);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the type of " + sourceDocUuid, e);
            return Access.UNKNOWN;
        }
        if (type.isEmpty()) {
            LOGGER.debug("A workspace file's source no longer exists, so the file can't be used");
            return Access.DENIED;
        }
        final DocumentPermission permission = documentContent
                ? DocumentPermission.VIEW
                : requiredPermission(type.get());
        try {
            return securityContext.hasDocumentPermission(new DocRef(type.get(), sourceDocUuid), permission)
                    ? Access.ALLOWED
                    : Access.DENIED;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check access to " + sourceDocUuid, e);
            return Access.UNKNOWN;
        }
    }

    /// @param type A document type.
    /// @return The permission needed to use what came from a document of the type: Use for a data source type,
    /// View for any other.
    public DocumentPermission requiredPermission(final String type) {
        return dataSourceTypes().contains(type)
                ? DocumentPermission.USE
                : DocumentPermission.VIEW;
    }

    // The source's type, from the Explorer tree, or else from the data sources that aren't in it. Empty if it
    // doesn't exist; a failure to look it up is thrown.
    private Optional<String> type(final String uuid) {
        final long now = clock.getAsLong();
        final CachedType cached = types.get(uuid);
        if (cached != null && cached.expiresAtMs() > now) {
            return Optional.of(cached.type());
        }
        final Optional<String> type = lookUpType(uuid);
        type.ifPresent(found -> {
            if (types.size() >= MAX_CACHED_TYPES) {
                types.clear();
            }
            types.put(uuid, new CachedType(found, now + TYPE_CACHE_TIME.toMillis()));
        });
        return type;
    }

    private Optional<String> lookUpType(final String uuid) {
        final Optional<String> type = explorerNodeServiceProvider.get().getNodeByUuid(uuid)
                .map(ExplorerNode::getType);
        if (type.isPresent()) {
            return type;
        }
        return dataSourceProviderRegistryProvider.get().findDataSourceByUuid(uuid)
                .map(DocRef::getType);
    }

    private Set<String> dataSourceTypes() {
        Set<String> result = dataSourceTypes;
        if (result == null) {
            result = Set.copyOf(dataSourceProviderRegistryProvider.get().getDataSourceTypes());
            dataSourceTypes = result;
        }
        return result;
    }

    // A source's type, and when to look it up again.
    private record CachedType(String type, long expiresAtMs) {

    }


    // --------------------------------------------------------------------------------


    /// Whether the user may still use what came from a source.
    public enum Access {
        /// They may.
        ALLOWED,
        /// They may not, or the source no longer exists.
        DENIED,
        /// It couldn't be checked, e.g. as a lookup failed for a moment.
        UNKNOWN
    }
}
