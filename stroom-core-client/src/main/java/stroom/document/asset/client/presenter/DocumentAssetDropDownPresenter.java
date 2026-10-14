package stroom.document.asset.client.presenter;

import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.docstore.shared.AbstractDoc;
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownView;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;

public class DocumentAssetDropDownPresenter extends MyPresenterWidget<DropDownView>
        implements DropDownUiHandlers, HasDataSelectionHandlers<String>, Focus {

    private final Provider<DocumentAssetChooserPresenter> chooserPresenterProvider;
    private AbstractDoc document;
    private String selectedAssetPath;
    private boolean enabled = true;

    @Inject
    public DocumentAssetDropDownPresenter(final EventBus eventBus,
                                          final DropDownView view,
                                          final Provider<DocumentAssetChooserPresenter> chooserPresenterProvider) {
        super(eventBus, view);
        this.chooserPresenterProvider = chooserPresenterProvider;
        view.setUiHandlers(this);
        setSelectedAssetPath(null);
    }

    public void setDocument(final AbstractDoc document) {
        this.document = document;
    }

    public String getSelectedAssetPath() {
        return selectedAssetPath;
    }

    public void setSelectedAssetPath(final String selectedAssetPath) {
        this.selectedAssetPath = selectedAssetPath;
        if (selectedAssetPath == null || selectedAssetPath.isEmpty()) {
            getView().setText("None", false);
        } else {
            getView().setText(selectedAssetPath, false);
        }
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @Override
    public void showPopup() {
        if (enabled && document != null) {
            final DocumentAssetChooserPresenter chooser = chooserPresenterProvider.get();
            chooser.setDocument(document);
            final ShowPopupEvent.Builder popupEventBuilder = ShowPopupEvent.builder(chooser);
            chooser.setupPopup(popupEventBuilder);
            popupEventBuilder.onHideRequest(event -> {
                if (event.isOk()) {
                    final String newUrl = chooser.getSelectedAssetUrl();
                    if (!Objects.equals(selectedAssetPath, newUrl)) {
                        setSelectedAssetPath(newUrl);
                        DataSelectionEvent.fire(DocumentAssetDropDownPresenter.this, newUrl, true);
                    }
                    event.hide();
                } else {
                    event.hide();
                }
            }).fire();
        }
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            getView().asWidget().getElement().addClassName("disabled");
        } else {
            getView().asWidget().getElement().removeClassName("disabled");
        }
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<String> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }
}
