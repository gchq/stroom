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

import stroom.ai.api.ElasticIndexPresence;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.template.ElasticIndexTemplateReader.ReadFailure;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;

/// Stroom AI's way to find whether an index name already stands for something on a cluster
/// ([ElasticIndexPresence]), with the cluster doc's connection, READ checked on the cluster doc. Nothing it meets
/// fails the call: what can't be known is [ElasticIndexPresence.State#UNKNOWN], with why.
public class AiElasticIndexPresence implements ElasticIndexPresence {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiElasticIndexPresence.class);

    private final ElasticIndexTemplateReader reader;

    /// @param reader Reads from the cluster.
    @Inject
    public AiElasticIndexPresence(final ElasticIndexTemplateReader reader) {
        this.reader = reader;
    }

    @Override
    public Presence check(final DocRef cluster, final String indexName) {
        if (cluster == null || NullSafe.isBlankString(cluster.getUuid())) {
            return Presence.unknown("No Elastic Cluster doc is given");
        }
        if (!ElasticClusterDoc.TYPE.equals(NullSafe.nonBlankStringElse(cluster.getType(), ElasticClusterDoc.TYPE))) {
            return Presence.unknown("Not an Elastic Cluster doc: " + cluster.getType());
        }
        if (NullSafe.isBlankString(indexName)) {
            return Presence.unknown("No index name is given");
        }
        final DocRef clusterRef = new DocRef(ElasticClusterDoc.TYPE, cluster.getUuid(), cluster.getName());
        try {
            return reader.presence(clusterRef, indexName.strip());
        } catch (final PermissionException e) {
            LOGGER.debug(() -> LogUtil.message("No permission to read Elastic Cluster {}: {}",
                    cluster.getUuid(), e.getMessage()));
            return Presence.unknown("The Elastic Cluster doc '" + NullSafe.nonBlankStringElse(cluster.getName(),
                    cluster.getUuid()) + "' can't be read by this user");
        } catch (final ReadFailure e) {
            return Presence.unknown(e.getMessage());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to look '{}' up on Elastic Cluster {}: {}",
                    indexName, cluster.getUuid(), e.getMessage()), e);
            return Presence.unknown(NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName()));
        }
    }
}
