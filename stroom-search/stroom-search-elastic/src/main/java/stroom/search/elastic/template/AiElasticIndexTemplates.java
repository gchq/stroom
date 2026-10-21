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

import stroom.ai.api.ElasticIndexTemplates;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.template.ElasticIndexTemplateReader.ReadFailure;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;

/// Stroom AI's way to read an Elastic Index doc's templates ([ElasticIndexTemplates]), with the same reading as
/// the REST endpoint ([ElasticIndexTemplateReader]): READ checked on the index doc, and the templates read with
/// its cluster connection.
public class AiElasticIndexTemplates implements ElasticIndexTemplates {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiElasticIndexTemplates.class);

    private final ElasticIndexTemplateReader reader;

    /// @param reader Reads the templates.
    @Inject
    public AiElasticIndexTemplates(final ElasticIndexTemplateReader reader) {
        this.reader = reader;
    }

    @Override
    public IndexTemplates read(final DocRef index) {
        Objects.requireNonNull(index, "index");
        if (!ElasticIndexDoc.TYPE.equals(index.getType())) {
            throw new ReadException(Reason.NOT_FOUND,
                    LogUtil.message("Elastic Index {} not found", index.getUuid()));
        }
        try {
            return toIndexTemplates(reader.read(index));
        } catch (final PermissionException e) {
            // Not told apart from a doc that doesn't exist
            LOGGER.debug(() -> LogUtil.message("No permission to read Elastic Index {}: {}",
                    index.getUuid(), e.getMessage()));
            throw new ReadException(Reason.NOT_FOUND,
                    LogUtil.message("Elastic Index {} not found", index.getUuid()));
        } catch (final ReadFailure e) {
            throw new ReadException(reasonOf(e), e.getMessage());
        }
    }

    private static Reason reasonOf(final ReadFailure failure) {
        return switch (failure.getFailure()) {
            case INDEX_NOT_FOUND -> Reason.NOT_FOUND;
            case NO_INDEX_NAME, NO_CLUSTER -> Reason.NOT_CONFIGURED;
            case CLUSTER_NOT_FOUND, CLUSTER_UNREACHABLE -> Reason.CLUSTER_UNAVAILABLE;
        };
    }

    private static IndexTemplates toIndexTemplates(final ElasticIndexTemplatesResponse response) {
        return new IndexTemplates(
                response.getIndexName(),
                response.getResolvedName(),
                response.getResolveMessage(),
                toIndexTemplate(response.getIndexTemplate()),
                NullSafe.list(response.getComponentTemplates())
                        .stream()
                        .map(AiElasticIndexTemplates::toComponentTemplate)
                        .toList());
    }

    private static IndexTemplate toIndexTemplate(final ElasticIndexTemplatesResponse.IndexTemplate indexTemplate) {
        if (indexTemplate == null) {
            return new IndexTemplate(Status.ERROR, null, null, List.of(),
                    "No index template was read");
        }
        return new IndexTemplate(
                toStatus(indexTemplate.getStatus()),
                indexTemplate.getName(),
                indexTemplate.getBody(),
                NullSafe.list(indexTemplate.getOverlapping())
                        .stream()
                        .map(AiElasticIndexTemplates::toOverlappingTemplate)
                        .toList(),
                indexTemplate.getMessage());
    }

    private static OverlappingTemplate toOverlappingTemplate(
            final ElasticIndexTemplatesResponse.OverlappingTemplate overlapping) {
        return new OverlappingTemplate(
                overlapping.getName(),
                overlapping.getIndexPatterns(),
                overlapping.getPriority());
    }

    private static ComponentTemplate toComponentTemplate(
            final ElasticIndexTemplatesResponse.ComponentTemplate componentTemplate) {
        return new ComponentTemplate(
                componentTemplate.getName(),
                toStatus(componentTemplate.getStatus()),
                componentTemplate.getBody(),
                componentTemplate.getMessage());
    }

    private static Status toStatus(final ElasticIndexTemplatesResponse.Status status) {
        if (status == null) {
            return Status.ERROR;
        }
        return switch (status) {
            case OK -> Status.OK;
            case NOT_FOUND -> Status.NOT_FOUND;
            case FORBIDDEN -> Status.FORBIDDEN;
            case ERROR -> Status.ERROR;
        };
    }
}
