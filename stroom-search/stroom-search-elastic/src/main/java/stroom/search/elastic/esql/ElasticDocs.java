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

package stroom.search.elastic.esql;

import stroom.docref.DocRef;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// The check that several Elastic Index documents can be read together, shared by the services that read more
/// than one ([ElasticEqlService], [ElasticKeyStreamsService]): Use on each (as for a search), each found with an
/// index name and a cluster, one cluster for them all, and their time fields as the reader needs them. Each service
/// says why it needs what it needs ([Rules]) and refuses in its own terms ([Refusals]).
final class ElasticDocs {

    private ElasticDocs() {
        // Static helpers only.
    }

    /// Checks the documents: Use on each, all found, each with an index name and a cluster, one cluster, and their
    /// time fields as the rules say.
    ///
    /// @param securityContext     Checks Use, and reads the documents as the user may for a search.
    /// @param elasticIndexStore   Reads the index documents.
    /// @param elasticClusterStore Reads their cluster document.
    /// @param indexes             The documents, in order.
    /// @param rules               What the reader needs of their time fields, and why.
    /// @param refusals            Makes the reader's refusal for a problem.
    /// @return The documents, their cluster and time field.
    /// @throws PermissionException if the user may not use one of them, naming it.
    static Checked check(final SecurityContext securityContext,
                         final ElasticIndexStore elasticIndexStore,
                         final ElasticClusterStore elasticClusterStore,
                         final List<DocRef> indexes,
                         final Rules rules,
                         final Refusals refusals) {
        if (NullSafe.isEmptyCollection(indexes)) {
            throw refusals.refusal(Problem.NOT_FOUND, "No Elastic Index was given", null);
        }
        for (final DocRef docRef : indexes) {
            if (docRef == null || !securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
                throw new PermissionException(securityContext.getUserRef(),
                        LogUtil.message("You are not authorised to search {}", docRef));
            }
        }
        return securityContext.useAsReadResult(() -> {
            final List<ElasticIndexDoc> found = new ArrayList<>();
            for (final DocRef docRef : indexes) {
                final ElasticIndexDoc indexDoc = elasticIndexStore.readDocument(docRef);
                if (indexDoc == null) {
                    throw refusals.refusal(Problem.NOT_FOUND, "Elastic Index " + docRef + " was not found", null);
                }
                if (NullSafe.isBlankString(indexDoc.getIndexName())) {
                    throw refusals.refusal(Problem.NOT_QUERYABLE, "Elastic Index '" + indexDoc.getName()
                                                                  + "' has no index name", null);
                }
                if (indexDoc.getClusterRef() == null) {
                    throw refusals.refusal(Problem.NOT_QUERYABLE, "Elastic Index '" + indexDoc.getName()
                                                                  + "' has no cluster", null);
                }
                if (rules.noTimeFieldWhy() != null && NullSafe.isBlankString(indexDoc.getTimeField())) {
                    throw refusals.refusal(Problem.NO_TIME_FIELD, "Elastic Index '" + indexDoc.getName() + "' has no "
                                                                  + "time field, " + rules.noTimeFieldWhy(), null);
                }
                found.add(indexDoc);
            }
            final Set<String> clusters = new LinkedHashSet<>();
            found.forEach(doc -> clusters.add(doc.getClusterRef().getUuid()));
            if (clusters.size() > 1) {
                final List<String> names = found.stream()
                        .map(doc -> "'" + doc.getName() + "' on cluster '"
                                    + NullSafe.nonBlankStringElse(doc.getClusterRef().getName(),
                                doc.getClusterRef().getUuid()) + "'")
                        .toList();
                throw refusals.refusal(Problem.DIFFERENT_CLUSTERS, "The Elastic Indexes are on different clusters ("
                                                                   + String.join(", ", names) + "): "
                                                                   + rules.clustersWhy(), names);
            }
            final Set<String> timeFields = new LinkedHashSet<>();
            found.stream()
                    .filter(doc -> NullSafe.isNonBlankString(doc.getTimeField()))
                    .forEach(doc -> timeFields.add(doc.getTimeField().strip()));
            if (rules.timeFieldsWhy() != null && timeFields.size() > 1) {
                final List<String> names = found.stream()
                        .map(doc -> "'" + doc.getName() + "' orders by '" + doc.getTimeField().strip() + "'")
                        .toList();
                throw refusals.refusal(Problem.DIFFERENT_TIME_FIELDS, "The Elastic Indexes have different time "
                                                                      + "fields (" + String.join(", ", names)
                                                                      + "): " + rules.timeFieldsWhy(), names);
            }
            final ElasticClusterDoc cluster = elasticClusterStore.readDocument(found.getFirst().getClusterRef());
            if (cluster == null) {
                throw refusals.refusal(Problem.NOT_QUERYABLE, "The Elastic Cluster of Elastic Index '"
                                                              + found.getFirst().getName() + "' was not found", null);
            }
            return new Checked(found.stream().map(doc -> doc.getIndexName().strip()).toList(),
                    timeFields.isEmpty()
                            ? null
                            : timeFields.iterator().next(),
                    cluster, found);
        });
    }


    // --------------------------------------------------------------------------------


    /// What can be wrong with the documents.
    enum Problem {
        /// None was given, or one doesn't exist.
        NOT_FOUND,
        /// One has no index name or cluster, or the cluster doesn't exist.
        NOT_QUERYABLE,
        /// One has no time field, which the reader needs.
        NO_TIME_FIELD,
        /// They are on different clusters.
        DIFFERENT_CLUSTERS,
        /// Their time fields differ, where the reader needs one.
        DIFFERENT_TIME_FIELDS
    }


    // --------------------------------------------------------------------------------


    /// Makes a reader's own refusal for a problem.
    @FunctionalInterface
    interface Refusals {

        /// @param problem What is wrong.
        /// @param message What to tell the caller.
        /// @param names   What the refusal is about, or null.
        /// @return The refusal to throw.
        RuntimeException refusal(Problem problem, String message, List<String> names);
    }


    // --------------------------------------------------------------------------------


    /// What a reader needs of the documents, each with why, as the end of its refusal's sentence.
    ///
    /// @param noTimeFieldWhy Why every document needs a time field (e.g. `which an EQL sequence orders its events
    ///                       by`), or null if one may have none.
    /// @param clustersWhy    Why they must share a cluster (e.g. `an EQL sequence searches one cluster`).
    /// @param timeFieldsWhy  Why they must share a time field, or null if theirs may differ.
    record Rules(String noTimeFieldWhy, String clustersWhy, String timeFieldsWhy) {

    }


    // --------------------------------------------------------------------------------


    /// The documents, checked.
    ///
    /// @param indexNames Each document's index name, in order.
    /// @param timeField  Their time field (the first document's that has one), or null if none has one.
    /// @param cluster    Their cluster.
    /// @param all        The documents, in order.
    record Checked(List<String> indexNames, String timeField, ElasticClusterDoc cluster, List<ElasticIndexDoc> all) {

        /// @return The first document.
        ElasticIndexDoc first() {
            return all.getFirst();
        }
    }
}
