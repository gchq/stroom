package stroom.document.asset.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.presenter.assets.DocumentAssetTree;
import stroom.document.asset.client.presenter.assets.DocumentAssetTreeItem;
import stroom.document.asset.shared.DocumentAssetResource;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.core.client.GWT;
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
    private final DocumentAssetTree tree;
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
        this.tree = new DocumentAssetTree("Assets");
        // Enter on a file, or a double click on one, chooses it, as OK does
        this.tree.setOpenHandler(item -> HidePopupRequestEvent.builder(this).fire());
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
            final DocumentAssetTreeItem item = event.getSelectedItem();
            if (item != null && item.isLeaf()) {
                selectedItem = item;
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
        /// Puts the asset tree in the view.
        ///
        /// @param tree The asset tree.
        void setTree(DocumentAssetTree tree);
    }
}
