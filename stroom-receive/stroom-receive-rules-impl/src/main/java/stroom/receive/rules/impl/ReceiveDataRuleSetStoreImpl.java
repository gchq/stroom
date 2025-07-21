package stroom.receive.rules.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docstore.api.AbstractSingletonDocumentStore;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QueryField.Builder;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A bit of a special store that only ever holds one doc with a hard coded name.
 */
@Singleton
public class ReceiveDataRuleSetStoreImpl
        extends AbstractSingletonDocumentStore<ReceiveDataRules>
        implements ReceiveDataRuleSetStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataRuleSetStoreImpl.class);
    private static final String LOCK_NAME = "ReceiveDataRuleSetStore";
    private static final String DOC_NAME = "Receive Data Rules";
    private static final AppPermissionSet REQUIRED_PERMISSIONS = AppPermissionSet.oneOf(
            AppPermission.MANAGE_DATA_RECEIPT_RULES_PERMISSION,
            AppPermission.STROOM_PROXY);

    private final Provider<StroomReceiptPolicyConfig> stroomReceiptPolicyConfigProvider;

    @Inject
    public ReceiveDataRuleSetStoreImpl(final StoreFactory storeFactory,
                                       final Serialiser2Factory serialiser2Factory,
                                       final SecurityContext securityContext,
                                       final Provider<StroomReceiptPolicyConfig> stroomReceiptPolicyConfigProvider,
                                       final ClusterLockService clusterLockService) {
        super(ReceiveDataRules.class, serialiser2Factory, storeFactory, securityContext, clusterLockService);
        this.stroomReceiptPolicyConfigProvider = stroomReceiptPolicyConfigProvider;
    }

    @Override
    public String getSingletonUuid() {
        return ReceiveDataRules.SINGLETON_UUID;
    }

    @Override
    public String getSingletonName() {
        return DOC_NAME;
    }

    @Override
    public AppPermissionSet getRequiredAppPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    @Override
    public String getType() {
        return ReceiveDataRules.TYPE;
    }

    @Override
    protected String getLockName() {
        return LOCK_NAME;
    }

    @Override
    protected BiFunction<ReceiveDataRules, DependencyRemapper, ReceiveDataRules> getReMapper() {
        return (doc, dependencyRemapper) -> {
            final List<ReceiveDataRule> rules = doc.getRules();
            if (NullSafe.hasItems(rules)) {
                final List<ReceiveDataRule> reMappedRules = new ArrayList<>(rules.size());
                for (final ReceiveDataRule rule : rules) {
                    if (rule != null) {
                        final ExpressionOperator remappedExpression = dependencyRemapper.remapExpression(
                                rule.getExpression());
                        final ReceiveDataRule reMappedRule = rule.copy()
                                .withExpression(remappedExpression)
                                .build();
                        reMappedRules.add(reMappedRule);
                    } else {
                        reMappedRules.add(rule);
                    }
                }
                return ReceiveDataRules.copy(doc)
                        .withRules(reMappedRules)
                        .build();
            } else {
                return doc;
            }
        };
    }

    //    @Override
//    public ReceiveDataRules getOrCreate() {
//        final DocRef docRef = lazyRulesDocRef.getValueWithLocks();
//        Objects.requireNonNull(docRef);
//        return readDocument(docRef);
//    }

//    private DocRef doGetOrCreate() {
//        // Should return 0-1 docs of our store's type, unless we have a problem
//        final List<DocRef> docRefs = store.list();
//        final DocRef docRef;
//        if (NullSafe.isEmptyCollection(docRefs)) {
//            docRef = clusterLockService.lockResult(LOCK_NAME, this::doGetOrCreateUnderLock);
//        } else {
//            docRef = getFirst(docRefs);
//        }
//        return docRef;
//    }


    @Override
    protected ReceiveDataRules onDocumentCreate(final ReceiveDataRules document) {
        final StroomReceiptPolicyConfig receiptPolicyConfig = stroomReceiptPolicyConfigProvider.get();
        final List<QueryField> fields = NullSafe.map(receiptPolicyConfig.getReceiptRulesInitialFields())
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    final String fieldName = entry.getKey();
                    final String typeName = entry.getValue();
                    final FieldType fieldType = FieldType.fromTypeName(typeName);
                    if (fieldType == null) {
                        LOGGER.error("Unknown field type in config '{}', ignoring.", typeName);
                        return null;
                    } else {
                        final Builder builder = QueryField.builder()
                                .fldName(fieldName)
                                .fldType(fieldType)
                                .conditionSet(ConditionSet.RECEIPT_POLICY_CONDITIONS);
                        return builder.build();
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        document.setFields(fields);
        return document;
    }

//    private DocRef doGetOrCreateUnderLock() {
//        // Re-check under lock
//        // Should return 0-1 docs of our store's type, unless we have a problem
//        final List<DocRef> docRefs = store.list();
//        final DocRef docRef;
//        if (NullSafe.isEmptyCollection(docRefs)) {
//            // Not there so create it
//            docRef = store.createDocument(DOC_NAME, getSingletonUuid());
//            final ReceiveDataRules receiveDataRules = store.readDocument(docRef);
//            final StroomReceiptPolicyConfig receiptPolicyConfig = stroomReceiptPolicyConfigProvider.get();
//            final List<QueryField> fields = NullSafe.map(receiptPolicyConfig.getReceiptRulesInitialFields())
//                    .entrySet()
//                    .stream()
//                    .sorted(Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
//                    .map(entry -> {
//                        final String fieldName = entry.getKey();
//                        final String typeName = entry.getValue();
//                        final FieldType fieldType = FieldType.fromTypeName(typeName);
//                        if (fieldType == null) {
//                            LOGGER.error("Unknown field type in config '{}', ignoring.", typeName);
//                            return null;
//                        } else {
//                            final Builder builder = QueryField.builder()
//                                    .fldName(fieldName)
//                                    .fldType(fieldType)
//                                    .conditionSet(ConditionSet.RECEIPT_POLICY_CONDITIONS);
//                            return builder.build();
//                        }
//                    })
//                    .filter(Objects::nonNull)
//                    .toList();
//
//            receiveDataRules.setFields(fields);
//            store.writeDocument(receiveDataRules);
//            LOGGER.info("Created document {}", docRef);
//        } else {
//            docRef = getFirst(docRefs);
//        }
//        return docRef;
//    }

//    private DocRef getFirst(final List<DocRef> docRefs) {
//        final DocRef docRef;
//        if (docRefs.size() > 1) {
//            throw new RuntimeException("Found multiple documents, expecting one. " + docRefs);
//        } else {
//            docRef = Objects.requireNonNull(docRefs.getFirst());
//            if (!Objects.equals(DOC_NAME, docRef.getName())) {
//                throw new RuntimeException("Unexpected document " + docRef);
//            }
//        }
//        return docRef;
//    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

//    @Override
//    public DocRef createDocument(final String name) {
//        return store.createDocument(name, ReceiveDataRules.SINGLETON_UUID);
//    }
//
//    @Override
//    public DocRef copyDocument(final DocRef docRef,
//                               final String name,
//                               final boolean makeNameUnique,
//                               final Set<String> existingNames) {
//        throw new UnsupportedOperationException("Copy not supported by Data Receipt Rules");
//    }
//
//    @Override
//    public DocRef moveDocument(final DocRef docRef) {
//        throw new UnsupportedOperationException("Move not supported by Data Receipt Rules");
//    }
//
//    @Override
//    public DocRef renameDocument(final DocRef docRef, final String name) {
//        throw new UnsupportedOperationException("Rename not supported by Data Receipt Rules");
//    }
//
//    @Override
//    public void deleteDocument(final DocRef docRef) {
//        throw new UnsupportedOperationException("Delete not supported by Data Receipt Rules");
//    }

//    @Override
//    public DocRefInfo info(final DocRef docRef) {
//        return store.info(docRef);
//    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

//    @Override
//    public Map<DocRef, Set<DocRef>> getDependencies() {
//        throw new UnsupportedOperationException("Get Dependencies not supported by Data Receipt Rules");
//    }
//
//    @Override
//    public Set<DocRef> getDependencies(final DocRef docRef) {
//        throw new UnsupportedOperationException("Get Dependencies not supported by Data Receipt Rules");
//    }

//    @Override
//    public void remapDependencies(final DocRef docRef,
//                                  final Map<DocRef, DocRef> remappings) {
//        store.remapDependencies(docRef, remappings, createMapper());
//    }
//
//    private BiConsumer<ReceiveDataRules, DependencyRemapper> createMapper() {
//        return (doc, dependencyRemapper) -> {
//            final List<ReceiveDataRule> templates = doc.getRules();
//            if (NullSafe.hasItems(templates)) {
//                templates.forEach(receiveDataRule -> {
//                    if (receiveDataRule.getExpression() != null) {
//                        dependencyRemapper.remapExpression(receiveDataRule.getExpression());
//                    }
//                });
//            }
//        };
//    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

//    @Override
//    public ReceiveDataRules readDocument(final DocRef docRef) {
//        return securityContext.secureResult(() ->
//                store.readDocument(docRef));
//    }
//
//    @Override
//    public ReceiveDataRules writeDocument(final ReceiveDataRules document) {
//        // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
//        // access it via the proc user (so long as use has MANAGE_DATA_RECEIPT_RULES_PERMISSION)
//        return securityContext.secureResult(AppPermission.MANAGE_DATA_RECEIPT_RULES_PERMISSION,
//                () -> securityContext.asProcessingUserResult(() -> store.writeDocument(document)));
//
//    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

//    @Override
//    public Set<DocRef> listDocuments() {
//        return store.listDocuments();
//    }
//
//    @Override
//    public DocRef importDocument(final DocRef docRef,
//                                 final Map<String, byte[]> dataMap,
//                                 final ImportState importState,
//                                 final ImportSettings importSettings) {
//        return store.importDocument(docRef, dataMap, importState, importSettings);
//    }
//
//    @Override
//    public Map<String, byte[]> exportDocument(final DocRef docRef,
//                                              final boolean omitAuditFields,
//                                              final List<Message> messageList) {
//        if (omitAuditFields) {
//            return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
//        }
//        return store.exportDocument(docRef, messageList, d -> d);
//    }
//
//    @Override
//    public String getType() {
//        return store.getType();
//    }
//
//    @Override
//    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
//        return null;
//    }
//
//    @Override
//    public boolean isSingleton() {
//        return true;
//    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

//    @Override
//    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
//        return store.findByNames(name, allowWildCards);
//    }

//    @Override
//    public Map<String, String> getIndexableData(final DocRef docRef) {
//        return store.getIndexableData(docRef);
//    }
}
