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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.IndexFieldProviders;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Singleton
public class IndexFieldProvidersImpl implements IndexFieldProviders {

    private final Map<String, IndexFieldProvider> providers = new HashMap<>();
    private final SecurityContext securityContext;

    @Inject
    IndexFieldProvidersImpl(final Set<IndexFieldProvider> indexFieldProviders,
                            final SecurityContext securityContext) {
        this.securityContext = securityContext;
        for (final IndexFieldProvider provider : indexFieldProviders) {
            providers.put(provider.getDataSourceType(), provider);
        }
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        Objects.requireNonNull(docRef, "Null DocRef supplied");
        Objects.requireNonNull(docRef.getType(), "Null DocRef type supplied");
        Objects.requireNonNull(fieldName, "Null field name supplied");

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to read {}", docRef));
        }
        final IndexFieldProvider provider = providers.get(docRef.getType());
        if (provider == null) {
            throw new NullPointerException("No provider can be found for: " + docRef.getType());
        }

        return provider.getIndexField(docRef, fieldName);
    }
}
