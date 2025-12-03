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

package stroom.state.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.ScyllaDbDocResource;
import stroom.state.shared.ScyllaDbTestResponse;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class ScyllaDbDocResourceImpl implements ScyllaDbDocResource, FetchWithUuid<ScyllaDbDoc> {

    private final Provider<ScyllaDbDocStore> scyllaDbStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    ScyllaDbDocResourceImpl(
            final Provider<ScyllaDbDocStore> scyllaDbStoreProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.scyllaDbStoreProvider = scyllaDbStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public ScyllaDbDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(scyllaDbStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public ScyllaDbDoc update(final String uuid, final ScyllaDbDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(scyllaDbStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(ScyllaDbDoc.TYPE)
                .build();
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Testing ScyllaDb Connection")
    public ScyllaDbTestResponse testCluster(final ScyllaDbDoc cluster) {
        try (final CqlSession session = CqlSession
                .builder()
                .withConfigLoader(DriverConfigLoader.fromString(cluster.getConnection()))
                .build()) {
            return new ScyllaDbTestResponse(true, "Session name = " + session.getName());
        } catch (final Exception e) {
            return new ScyllaDbTestResponse(false, e.getMessage());
        }
    }
}
