package stroom.index.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.ClientDocumentType;
import stroom.document.client.ClientDocumentTypeRegistry;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.index.client.presenter.IndexPresenter;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class IndexPlugin extends DocumentPlugin<LuceneIndexDoc> {

    private static final IndexResource INDEX_RESOURCE = GWT.create(IndexResource.class);
    public static final ClientDocumentType DOCUMENT_TYPE = new ClientDocumentType(
            DocumentTypeGroup.INDEXING,
            LuceneIndexDoc.DOCUMENT_TYPE,
            "Lucene Index",
            SvgImage.DOCUMENT_INDEX);

    private final Provider<IndexPresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public IndexPlugin(final EventBus eventBus,
                       final Provider<IndexPresenter> editorProvider,
                       final RestFactory restFactory,
                       final ContentManager contentManager,
                       final DocumentPluginEventManager entityPluginEventManager,
                       final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;

        ClientDocumentTypeRegistry.put(DOCUMENT_TYPE);
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<LuceneIndexDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(INDEX_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final LuceneIndexDoc document,
                     final Consumer<LuceneIndexDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(INDEX_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return LuceneIndexDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final LuceneIndexDoc document) {
        return DocRefUtil.create(document);
    }
}

