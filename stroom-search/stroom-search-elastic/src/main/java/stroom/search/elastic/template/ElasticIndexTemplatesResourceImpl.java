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

package stroom.search.elastic.template;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.template.ElasticIndexTemplateReader.ReadFailure;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import java.util.Map;
import java.util.Objects;

/// Reads an Elastic Index doc's templates from its cluster, with the doc's cluster connection as a search
/// uses it, so the caller needs READ on the index doc but nothing on the cluster doc. The reading is
/// [ElasticIndexTemplateReader]'s; this turns its failures into HTTP statuses.
@AutoLogged
public class ElasticIndexTemplatesResourceImpl implements ElasticIndexTemplatesResource {

    private final Provider<ElasticIndexTemplateReader> readerProvider;

    /// @param readerProvider Reads the templates.
    @Inject
    public ElasticIndexTemplatesResourceImpl(final Provider<ElasticIndexTemplateReader> readerProvider) {
        this.readerProvider = readerProvider;
    }

    @Override
    @AutoLogged(value = OperationType.VIEW, verb = "Reading Elasticsearch index templates")
    public ElasticIndexTemplatesResponse fetchTemplates(final ElasticIndexTemplatesRequest request) {
        final DocRef docRef = getIndexDocRef(request);
        try {
            // A PermissionException (403) for want of READ on the doc is left to its exception mapper
            return readerProvider.get().read(docRef);
        } catch (final ReadFailure e) {
            throw error(statusOf(e), e.getMessage());
        }
    }

    private DocRef getIndexDocRef(final ElasticIndexTemplatesRequest request) {
        // A DocRef always has a type and UUID, else it would not have been deserialised
        final DocRef docRef = NullSafe.get(request, ElasticIndexTemplatesRequest::getIndexDocRef);
        if (docRef == null) {
            throw error(Status.BAD_REQUEST, "indexDocRef is required");
        }
        if (!ElasticIndexDoc.TYPE.equals(docRef.getType())) {
            throw error(Status.BAD_REQUEST, LogUtil.message(
                    "indexDocRef must be of type {}, not {}", ElasticIndexDoc.TYPE, docRef.getType()));
        }
        return docRef;
    }

    private static Status statusOf(final ReadFailure failure) {
        return switch (failure.getFailure()) {
            case INDEX_NOT_FOUND -> Status.NOT_FOUND;
            case NO_INDEX_NAME, NO_CLUSTER -> Status.BAD_REQUEST;
            case CLUSTER_NOT_FOUND, CLUSTER_UNREACHABLE -> Status.SERVICE_UNAVAILABLE;
        };
    }

    // A response with a body like the one the exception mappers give, so the caller sees the reason
    private static WebApplicationException error(final Status status, final String message) {
        return new WebApplicationException(message, jakarta.ws.rs.core.Response
                .status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "code", status.getStatusCode(),
                        "message", Objects.requireNonNullElse(message, status.getReasonPhrase())))
                .build());
    }
}
