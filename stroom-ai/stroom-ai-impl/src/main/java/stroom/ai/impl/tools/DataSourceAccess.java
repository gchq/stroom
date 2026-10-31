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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// How the query tools find data sources (anything a search can run on: indexes, views, Elastic and Solr indexes,
/// statistics and so on) as the user. A data source needs Use, as running a query on it does; one the user can't
/// use is treated exactly as one that doesn't exist, so nothing reveals whether it does.
public class DataSourceAccess {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceAccess.class);

    /// What a tool says for a data source the user can't use, or that doesn't exist, which it doesn't tell apart.
    public static final String NOT_FOUND = "There is no data source with that name or uuid that you can use.";

    private final Provider<DataSourceProviderRegistry> registryProvider;
    private final SecurityContext securityContext;

    /// @param registryProvider Lists the data sources and their fields.
    /// @param securityContext  Checks the user's permissions.
    @Inject
    DataSourceAccess(final Provider<DataSourceProviderRegistry> registryProvider,
                     final SecurityContext securityContext) {
        this.registryProvider = registryProvider;
        this.securityContext = securityContext;
    }

    /// @return The data sources the user can use, by name. The stores list only those the user can view, and each
    /// is checked for Use as well.
    public List<DocRef> listUsable() {
        return NullSafe.list(registryProvider.get().getDataSourceDocRefs()).stream()
                .filter(this::canUse)
                .sorted(Comparator.comparing((DocRef docRef) -> NullSafe.string(docRef.getName())
                                .toLowerCase(Locale.ROOT))
                        .thenComparing(DocRef::getType))
                .toList();
    }

    /// @param docRef A data source.
    /// @return True if the user can use it. False if they can't, or if checking fails (failing closed).
    public boolean canUse(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null) {
            return false;
        }
        try {
            return securityContext.hasDocumentPermission(docRef, DocumentPermission.USE);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check Use on " + docRef, e);
            return false;
        }
    }

    /// @param uuidOrName A data source's uuid or name, as the model gave it.
    /// @return The data sources it names that the user can use: one for a uuid, and any with the name (ignoring
    /// case if none match exactly).
    public List<DocRef> find(final String uuidOrName) {
        if (uuidOrName == null || uuidOrName.isBlank()) {
            return List.of();
        }
        final String given = uuidOrName.strip();
        try {
            final Optional<DocRef> byUuid = registryProvider.get().findDataSourceByUuid(given);
            if (byUuid.isPresent()) {
                return byUuid.filter(this::canUse).stream().toList();
            }
            final List<DocRef> usable = listUsable();
            final List<DocRef> exact = usable.stream()
                    .filter(docRef -> given.equals(docRef.getName()))
                    .toList();
            return !exact.isEmpty()
                    ? exact
                    : usable.stream()
                            .filter(docRef -> given.equalsIgnoreCase(docRef.getName()))
                            .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find data source " + given, e);
            return List.of();
        }
    }

    /// @param docRef A data source the user can use.
    /// @return Its default time field, if it has one.
    public Optional<QueryField> timeField(final DocRef docRef) {
        try {
            return securityContext.useAsReadResult(() -> registryProvider.get()
                    .getDataSourceProvider(docRef.getType())
                    .flatMap(provider -> provider.getTimeField(docRef)));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the time field of " + docRef, e);
            return Optional.empty();
        }
    }

    /// @param docRef A data source the user can use.
    /// @return Its documentation, if it has any.
    public Optional<String> documentation(final DocRef docRef) {
        try {
            return securityContext.useAsReadResult(() -> registryProvider.get().fetchDocumentation(docRef))
                    .filter(text -> !text.isBlank());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to fetch the documentation of " + docRef, e);
            return Optional.empty();
        }
    }

    /// @param docRef A data source.
    /// @return What it is, e.g. "View 'Windows events' (uuid 1234…)".
    public static String describe(final DocRef docRef) {
        return docRef.getType() + " '" + NullSafe.nonBlankStringElse(docRef.getName(), docRef.getUuid())
               + "' (uuid " + docRef.getUuid() + ")";
    }
}
