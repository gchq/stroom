package stroom.document.asset.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.presenter.assets.DocumentAssetTreeItem;
import stroom.document.asset.shared.DocumentAssetResource;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashSet;
import java.util.Set;

public class DocumentAssetChooserPresenter
        extends MyPresenterWidget<DocumentAssetChooserPresenter.DocumentAssetChooserView> {

    private static final DocumentAssetResource DOCUMENT_ASSET_RESOURCE =
            GWT.create(DocumentAssetResource.class);

    private final RestFactory restFactory;
    private final Tree tree;
    private final Set<String> treeItemPathToOpenState = new HashSet<>();
    private AbstractDoc document;
    private DocumentAssetTreeItem selectedItem;

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 450;

    @Inject
    public DocumentAssetChooserPresenter(final EventBus eventBus,
                                         final DocumentAssetChooserView view,
                                         final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.tree = new Tree(new AssetTreeResources(), true);
        this.tree.setStylePrimaryName("visualisation-asset-tree");
        view.setTree(tree);
    }

    public void setDocument(final AbstractDoc document) {
        this.document = document;
    }

    public void setupPopup(final ShowPopupEvent.Builder builder) {
        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Choose Asset")
                .modal(true);

        // Disable OK button initially
        DisablePopupEvent.builder(this).action(DialogAction.OK).fire();

        if (document != null) {
            fetchDraftAssets();
        }
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(tree.addSelectionHandler(event -> {
            final TreeItem item = event.getSelectedItem();
            if (item instanceof DocumentAssetTreeItem && ((DocumentAssetTreeItem) item).isLeaf()) {
                selectedItem = (DocumentAssetTreeItem) item;
                EnablePopupEvent.builder(this).action(DialogAction.OK).fire();
            } else {
                selectedItem = null;
                DisablePopupEvent.builder(this).action(DialogAction.OK).fire();
            }
        }));
    }

    private void fetchDraftAssets() {
        final String ownerId = document.getUuid();
        DocumentAssetPresenterUtils.storeOpenClosedState(tree, treeItemPathToOpenState);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.fetchDraftAssets(ownerId))
                .onSuccess(assets -> {
                    tree.clear();
                    DocumentAssetPresenterUtils.addPathsToTree(tree, assets.getAssets());
                    DocumentAssetPresenterUtils.restoreOpenClosedState(tree, treeItemPathToOpenState);
                    selectedItem = null;
                    DisablePopupEvent.builder(this).action(DialogAction.OK).fire();
                })
                .taskMonitorFactory(this)
                .exec();
    }

    public String getSelectedAssetUrl() {
        if (selectedItem != null && document != null) {
            return DocumentAssetPresenter.ASSET_SERVLET_PATH_PREFIX
                    + document.getUuid()
                    + DocumentAssetPresenterUtils.getItemPath(selectedItem);
        }
        return null;
    }

    public interface DocumentAssetChooserView extends View {
        void setTree(Tree tree);
    }

    private static class AssetTreeResources implements Tree.Resources {
        private static final int HEIGHT = 12;
        private static final int WIDTH = 16;
        private static final stroom.document.asset.client.presenter.assets.DocumentAssetImageResource CLOSED =
                new stroom.document.asset.client.presenter.assets.DocumentAssetImageResource(
                        HEIGHT,
                        WIDTH,
                        "/ui/background-images/arrow-right.png");
        private static final stroom.document.asset.client.presenter.assets.DocumentAssetImageResource OPEN =
                new stroom.document.asset.client.presenter.assets.DocumentAssetImageResource(
                        HEIGHT,
                        WIDTH,
                        "/ui/background-images/arrow-down.png");
        private static final stroom.document.asset.client.presenter.assets.DocumentAssetImageResource TRANSPARENT =
                new stroom.document.asset.client.presenter.assets.DocumentAssetImageResource(
                        HEIGHT,
                        WIDTH,
                        "/ui/background-images/transparent-16x16.png");

        @Override
        public com.google.gwt.resources.client.ImageResource treeClosed() {
            return CLOSED;
        }

        @Override
        public com.google.gwt.resources.client.ImageResource treeLeaf() {
            return TRANSPARENT;
        }

        @Override
        public com.google.gwt.resources.client.ImageResource treeOpen() {
            return OPEN;
        }
    }
}
