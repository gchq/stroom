package stroom.docstore.api;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.shared.AbstractSingletonDoc;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermissionSet;
import stroom.util.shared.Message;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A bit of a special store that only ever holds one doc with a hard coded name/uuid.
 */
public abstract class AbstractSingletonDocumentStore<D extends AbstractSingletonDoc>
        implements SingletonDocumentStore<D> {

    private final Store<D> store;
    private final SecurityContext securityContext;
    private final ClusterLockService clusterLockService;
    private final AppPermissionSet requiredPermissions;
    private final DocRef singletonDocRef;

    public AbstractSingletonDocumentStore(final Class<D> documentClass,
                                          final Serialiser2Factory serialiser2Factory,
                                          final StoreFactory storeFactory,
                                          final SecurityContext securityContext,
                                          final ClusterLockService clusterLockService) {
        this.clusterLockService = clusterLockService;
        this.securityContext = securityContext;
        this.store = storeFactory.createStore(
                serialiser2Factory.createSerialiser(documentClass),
                getType(),
                documentClass);
        this.requiredPermissions = Objects.requireNonNullElseGet(
                getRequiredAppPermissions(),
                AppPermissionSet::empty);
        this.singletonDocRef = DocRef.builder()
                .uuid(Objects.requireNonNull(getSingletonUuid()))
                .type(Objects.requireNonNull(getType()))
                .name(Objects.requireNonNull(getSingletonName()))
                .build();
    }

    /**
     * The type of the document provided by this document store
     */
    @Override
    public abstract String getType();

    /**
     * The UUID of the singleton document
     */
    @Override
    public abstract String getSingletonUuid();

    /**
     * The name of the singleton document
     */
    public abstract String getSingletonName();

    /**
     * The app perms required to access this document
     */
    protected abstract AppPermissionSet getRequiredAppPermissions();

    /**
     * The name of the lock to use when creating the singleton document
     */
    protected abstract String getLockName();

    /**
     * Called at the point of creation of the document, allowing the subclass to further modify the
     * document. For a no-op, just return document.
     *
     * @param document The created document
     * @return The modified document
     */
    protected abstract D onDocumentCreate(final D document);

    protected abstract BiFunction<D, DependencyRemapper, D> getReMapper();

    public DocRef getSingletonDocRef() {
        return singletonDocRef;
    }

    @Override
    public D getOrCreate() {
        return securityContext.secureResult(requiredPermissions, () -> {
            // Doc perms are needed to read a document but the user will never have any as this
            // is a singleton, so once we have checked they have the app perm(s) needed, we just
            // run as proc user
            return securityContext.asProcessingUserResult(this::doGetOrCreate);
        });
    }

    private D doGetOrCreate() {
        // Should return 0-1 docs of our store's type, unless we have a problem
        D doc = fetchSingletonDoc();
        if (doc == null) {
            doc = clusterLockService.lockResult(
                    Objects.requireNonNull(getLockName()),
                    this::doGetOrCreateUnderLock);
        }
        Objects.requireNonNull(doc, "Something has gone wrong, doc should exist by now");
        return doc;
    }

    private D fetchSingletonDoc() {
        try {
            return store.readDocument(singletonDocRef);
        } catch (final DocumentNotFoundException e) {
            // Swallow
            return null;
        }
    }

    private D doGetOrCreateUnderLock() {
        // Re-check under lock
        D doc = fetchSingletonDoc();
        if (doc == null) {
            final DocRef docRef = store.createDocument(getSingletonName(), getSingletonUuid());
            doc = store.readDocument(docRef);

            // Allow the subclass to init the doc in any way it sees fit
            final D modifiedDoc = onDocumentCreate(doc);
            if (!Objects.equals(doc, modifiedDoc)) {
                store.writeDocument(modifiedDoc);
                doc = modifiedDoc;
            }
        }
        return doc;
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return getWithPermCheck(() ->
                store.getIndexableData(docRef));
    }

    private <T> T getWithPermCheck(final Supplier<T> supplier) {
        Objects.requireNonNull(supplier);
        return securityContext.secureResult(requiredPermissions, () -> {
            // Doc perms are needed to read a document but the user will never have any as this
            // is a singleton, so once we have checked they have the app perm(s) needed, we just
            // run as proc user
            return securityContext.asProcessingUserResult(supplier);
        });
    }

    @Override
    public D readDocument(final DocRef docRef) {
        return getWithPermCheck(() ->
                store.readDocument(docRef));
    }

    @Override
    public D writeDocument(final D document) {
        return getWithPermCheck(() ->
                store.writeDocument(document));
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return getWithPermCheck(() ->
                store.info(docRef));
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        return getWithPermCheck(() ->
                store.importDocument(docRef, dataMap, importState, importSettings));
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        return getWithPermCheck(() -> {
            if (omitAuditFields) {
                return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
            }
            return store.exportDocument(docRef, messageList, d -> d);
        });
    }

    @Override
    public Set<DocRef> listDocuments() {
        return Collections.singleton(getSingletonDocRef());
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return null;
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return null;
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        getWithPermCheck(() -> {
            store.remapDependencies(docRef, remappings, Objects.requireNonNull(getReMapper()));
            return null;
        });
    }
}
