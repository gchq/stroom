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

package stroom.data.retention.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.concurrent.LazyValue;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

@Singleton
class DataRetentionRulesServiceImpl
        extends AbstractDocumentStore<DataRetentionRules>
        implements DataRetentionRulesService, DataRetentionRulesProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataRetentionRulesServiceImpl.class);
    private static final String POLICY_NAME = "Data Retention";
    private static final String LOCK_NAME = "DataRetentionRulesCreation";

    private final SecurityContext securityContext;
    private final ClusterLockService clusterLockService;
    private final LazyValue<DocRef> lazyDocRef = LazyValue.initialisedBy(this::doGetOrCreate);

    @Inject
    DataRetentionRulesServiceImpl(final StoreFactory storeFactory,
                                  final Serialiser2Factory serialiser2Factory,
                                  final SecurityContext securityContext,
                                  final ClusterLockService clusterLockService) {
        super(storeFactory,
                serialiser2Factory.createSerialiser(
                        DataRetentionRules.class),
                DataRetentionRules.TYPE,
                DataRetentionRules::builder,
                DataRetentionRules::copy);

        this.securityContext = securityContext;
        this.clusterLockService = clusterLockService;
    }

    @Override
    protected DependencyRemapFunction<DataRetentionRules> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> {
            final List<DataRetentionRule> rules = doc.getRules();
            if (rules != null && !rules.isEmpty()) {
                rules.forEach(receiveDataRule -> {
                    if (receiveDataRule.getExpression() != null) {
                        dependencyRemapper.remapExpression(receiveDataRule.getExpression());
                    }
                });
            }
            return doc;
        };
    }

    @Override
    public DataRetentionRules readDocument(final DocRef docRef) {
        return securityContext.secureResult(() ->
                getStore().readDocument(docRef));
    }

    @Override
    public DataRetentionRules writeDocument(final DataRetentionRules document) {
        // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
        // access it via the proc user (so long as use has MANAGE_POLICIES_PERMISSION)
        return securityContext.secureResult(AppPermission.MANAGE_POLICIES_PERMISSION,
                () -> securityContext.asProcessingUserResult(() -> getStore().writeDocument(document)));

    }

    @Override
    public DataRetentionRules getOrCreate() {
        // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
        // access it via the proc user.
        return securityContext.asProcessingUserResult(() -> {
            final DocRef docRef = lazyDocRef.getValueWithLocks();
            Objects.requireNonNull(docRef);
            return readDocument(docRef);
        });
    }

    private DocRef doGetOrCreate() {
        // Should return 0-1 docs of our store's type, unless we have a problem
        DocRef docRef = getSingletonDoc();
        if (docRef == null) {
            docRef = clusterLockService.lockResult(LOCK_NAME, () -> {
                DocRef docRef2 = getSingletonDoc();
                if (docRef2 == null) {
                    // Not there so create it
                    docRef2 = createDocument(POLICY_NAME);
                    LOGGER.info("Created document {}", docRef2);
                }
                return docRef2;
            });
        }
        return docRef;
    }

    private DocRef getSingletonDoc() {
        final List<DocRef> docRefs = getStore().list();
        final DocRef docRef;
        if (NullSafe.isEmptyCollection(docRefs)) {
            docRef = null;
        } else {
            if (docRefs.size() > 1) {
                throw new RuntimeException("Found multiple documents, expecting one. " + docRefs);
            } else {
                docRef = Objects.requireNonNull(docRefs.getFirst());
                if (!(Objects.equals(POLICY_NAME, docRef.getName())
                      || Objects.equals(POLICY_NAME, docRef.getUuid()))) {
                    throw new RuntimeException("Unexpected document " + docRef);
                }
            }
        }
        return docRef;
    }
}
