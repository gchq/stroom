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

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.security.client.presenter.AppPermissionsPresenter.AppPermissionsView;
import stroom.security.shared.AppUserPermissions;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;
import javax.inject.Provider;

public class AppPermissionsPresenter
        extends ContentTabPresenter<AppPermissionsView> {

    private final RestFactory restFactory;
    private final Provider<AppPermissionsChangePresenter> appPermissionsChangePresenterProvider;
    private final AppUserPermissionsListPresenter appUserPermissionsListPresenter;
    private final Provider<DocSelectionPopup> docSelectionPopupProvider;

    private final ButtonView edit;

    @Inject
    public AppPermissionsPresenter(
            final EventBus eventBus,
            final AppPermissionsView view,
            final RestFactory restFactory,
            final AppUserPermissionsListPresenter appUserPermissionsListPresenter,
            final Provider<AppPermissionsChangePresenter> appPermissionsChangePresenterProvider,
            final Provider<DocSelectionPopup> docSelectionPopupProvider) {

        super(eventBus, view);
        this.restFactory = restFactory;
        this.appPermissionsChangePresenterProvider = appPermissionsChangePresenterProvider;
        this.appUserPermissionsListPresenter = appUserPermissionsListPresenter;
        this.docSelectionPopupProvider = docSelectionPopupProvider;
        view.setPermissionsView(appUserPermissionsListPresenter.getView());

        edit = appUserPermissionsListPresenter.addButton(SvgPresets.EDIT);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(appUserPermissionsListPresenter.getSelectionModel().addSelectionHandler(e -> {
            edit.setEnabled(appUserPermissionsListPresenter.getSelectionModel().getSelected() != null);
            if (e.getSelectionType().isDoubleSelect()) {
                editPermissions();
            }
        }));
        registerHandler(edit.addClickHandler(e -> editPermissions()));
    }

    private void editPermissions() {
        final AppUserPermissions appUserPermissions = appUserPermissionsListPresenter.getSelectionModel().getSelected();
        if (appUserPermissions != null) {
            final AppPermissionsChangePresenter appPermissionsChangePresenter =
                    appPermissionsChangePresenterProvider.get();
            appPermissionsChangePresenter.show(appUserPermissions.getUserRef(), () ->
                    appUserPermissionsListPresenter.refresh());
        }
    }

    public void refresh() {
        appUserPermissionsListPresenter.refresh();
    }

//    public void show() {
//        appUserPermissionsListPresenter.refresh();
//
//        final PopupSize popupSize = PopupSize.builder()
//                .width(Size
//                        .builder()
//                        .initial(1000)
//                        .min(1000)
//                        .resizable(true)
//                        .build())
//                .height(Size
//                        .builder()
//                        .initial(800)
//                        .min(800)
//                        .resizable(true)
//                        .build())
//                .build();
//
//        ShowPopupEvent.builder(this)
//                .popupType(PopupType.CLOSE_DIALOG)
//                .popupSize(popupSize)
//                .caption("Application Permissions")
//                .modal()
//                .fire();
//    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.SHIELD;
    }

    @Override
    public String getLabel() {
        return "Application Permissions";
    }

    @Override
    public String getType() {
        return "ApplicationPermissions";
    }

    public interface AppPermissionsView extends View {

        void setPermissionsView(View view);
    }
}
