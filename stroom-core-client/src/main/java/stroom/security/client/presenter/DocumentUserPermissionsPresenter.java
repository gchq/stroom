/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.security.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter.DocumentUserPermissionsView;
import stroom.security.shared.DocumentUserPermissions;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;
import stroom.widget.util.client.MouseUtil;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentUserPermissionsPresenter
        extends MyPresenterWidget<DocumentUserPermissionsView> {

    private final DocumentUserPermissionsListPresenter documentUserPermissionsListPresenter;
    private final Provider<DocumentUserPermissionsEditPresenter> documentUserPermissionsEditPresenterProvider;
    private final ButtonView docEdit;
    private final InlineSvgToggleButton explicitOnly;
    private DocRef docRef;

    @Inject
    public DocumentUserPermissionsPresenter(
            final EventBus eventBus,
            final DocumentUserPermissionsView view,
            final DocumentUserPermissionsListPresenter documentUserPermissionsListPresenter,
            final Provider<DocumentUserPermissionsEditPresenter> documentUserPermissionsEditPresenterProvider) {

        super(eventBus, view);
        this.documentUserPermissionsListPresenter = documentUserPermissionsListPresenter;
        this.documentUserPermissionsEditPresenterProvider = documentUserPermissionsEditPresenterProvider;
        view.setPermissionsView(documentUserPermissionsListPresenter.getView());

        docEdit = documentUserPermissionsListPresenter.addButton(new Preset(
                SvgImage.EDIT,
                "Edit Permissions For Selected User",
                false));

        explicitOnly = new InlineSvgToggleButton();
        explicitOnly.setSvg(SvgImage.EYE_OFF);
        explicitOnly.setTitle("Only Show Users With Explicit Permissions");
        explicitOnly.setState(false);
        documentUserPermissionsListPresenter.addButton(explicitOnly);
        documentUserPermissionsListPresenter.setAllUsers(!explicitOnly.getState());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(documentUserPermissionsListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final DocumentUserPermissions selected =
                    documentUserPermissionsListPresenter.getSelectionModel().getSelected();
            docEdit.setEnabled(selected != null);
            if (e.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
        registerHandler(docEdit.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onEdit();
            }
        }));
        registerHandler(explicitOnly.addClickHandler(e -> {
            if (explicitOnly.getState()) {
                explicitOnly.setTitle("Show All Users");
                explicitOnly.setSvg(SvgImage.EYE);
            } else {
                explicitOnly.setTitle("Only Show Users With Explicit Permissions");
                explicitOnly.setSvg(SvgImage.EYE_OFF);
            }
            documentUserPermissionsListPresenter.setAllUsers(!explicitOnly.getState());
            documentUserPermissionsListPresenter.refresh();
        }));
    }

    private void onEdit() {
        final DocumentUserPermissions selected =
                documentUserPermissionsListPresenter.getSelectionModel().getSelected();
        if (selected != null) {
            documentUserPermissionsEditPresenterProvider.get().show(docRef, selected, () ->
                    documentUserPermissionsListPresenter.refresh());
        }
    }

    public void show(final DocRef docRef) {
        this.docRef = docRef;
        documentUserPermissionsListPresenter.setDocRef(docRef);
        documentUserPermissionsListPresenter.refresh();

        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(1000)
                        .min(1000)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .initial(800)
                        .min(800)
                        .resizable(true)
                        .build())
                .build();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Permissions For '" + docRef.getDisplayValue() + "'")
                .modal()
                .fire();
    }

    public interface DocumentUserPermissionsView extends View {

        void setPermissionsView(View view);
    }
}
