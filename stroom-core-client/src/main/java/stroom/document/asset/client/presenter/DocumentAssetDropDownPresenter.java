package stroom.document.asset.client.presenter;

import stroom.docstore.shared.AbstractDoc;
import stroom.widget.component.client.Listeners;
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Objects;
import java.util.function.Consumer;

public class DocumentAssetDropDownPresenter extends MyPresenterWidget<DropDownView>
        implements DropDownUiHandlers, Focus {

    private final Provider<DocumentAssetChooserPresenter> chooserPresenterProvider;
    private AbstractDoc document;
    private String selectedAssetPath;
    private boolean enabled = true;

    private final Listeners<String> selectionListeners = new Listeners<>();

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
            getView().setText("None");
        } else {
            getView().setText(selectedAssetPath);
        }
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @Override
    public void showPopup() {
        if (enabled && document != null) {
            final DocumentAssetChooserPresenter chooser = makeDialog(chooserPresenterProvider);
            chooser.setDocument(document);
            final ShowPopupEvent.Builder popupEventBuilder = ShowPopupEvent.builder(chooser);
            chooser.setupPopup(popupEventBuilder);
            popupEventBuilder.onHideRequest(event -> {
                if (event.isOk()) {
                    final String newUrl = chooser.getSelectedAssetUrl();
                    if (!Objects.equals(selectedAssetPath, newUrl)) {
                        setSelectedAssetPath(newUrl);
                        selectionListeners.fire(newUrl);
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

    /// Listens for the user choosing an asset, until the registration is removed.
    ///
    /// @param listener Told what is selected.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<String> listener) {
        return selectionListeners.add(listener);
    }
}
