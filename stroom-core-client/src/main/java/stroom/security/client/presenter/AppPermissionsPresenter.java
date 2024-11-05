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
import stroom.security.client.presenter.AppPermissionsPresenter.AppPermissionsView;
import stroom.security.shared.AppUserPermissions;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;
import javax.inject.Provider;

public class AppPermissionsPresenter
        extends ContentTabPresenter<AppPermissionsView> {

    private final Provider<AppPermissionsEditPresenter> appPermissionsChangePresenterProvider;
    private final AppUserPermissionsListPresenter appUserPermissionsListPresenter;

    private final ButtonView edit;
    private final InlineSvgToggleButton explicitOnly;

    @Inject
    public AppPermissionsPresenter(
            final EventBus eventBus,
            final AppPermissionsView view,
            final AppUserPermissionsListPresenter appUserPermissionsListPresenter,
            final Provider<AppPermissionsEditPresenter> appPermissionsChangePresenterProvider) {

        super(eventBus, view);
        this.appPermissionsChangePresenterProvider = appPermissionsChangePresenterProvider;
        this.appUserPermissionsListPresenter = appUserPermissionsListPresenter;
        view.setPermissionsView(appUserPermissionsListPresenter.getView());

        edit = appUserPermissionsListPresenter.addButton(SvgPresets.EDIT);

        explicitOnly = new InlineSvgToggleButton();
        explicitOnly.setSvg(SvgImage.EYE_OFF);
        explicitOnly.setTitle("Only Show Users With Explicit Permissions");
        explicitOnly.setState(false);
        appUserPermissionsListPresenter.addButton(explicitOnly);
        appUserPermissionsListPresenter.setAllUsers(!explicitOnly.getState());
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
        registerHandler(explicitOnly.addClickHandler(e -> {
            if (explicitOnly.getState()) {
                explicitOnly.setTitle("Show All Users");
                explicitOnly.setSvg(SvgImage.EYE);
            } else {
                explicitOnly.setTitle("Only Show Users With Explicit Permissions");
                explicitOnly.setSvg(SvgImage.EYE_OFF);
            }
            appUserPermissionsListPresenter.setAllUsers(!explicitOnly.getState());
            appUserPermissionsListPresenter.refresh();
        }));
    }

    private void editPermissions() {
        final AppUserPermissions appUserPermissions = appUserPermissionsListPresenter.getSelectionModel().getSelected();
        if (appUserPermissions != null) {
            final AppPermissionsEditPresenter appPermissionsChangePresenter =
                    appPermissionsChangePresenterProvider.get();
            appPermissionsChangePresenter.show(appUserPermissions.getUserRef(),
                    appUserPermissionsListPresenter::refresh);
        }
    }

    public void refresh() {
        appUserPermissionsListPresenter.refresh();
    }

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
