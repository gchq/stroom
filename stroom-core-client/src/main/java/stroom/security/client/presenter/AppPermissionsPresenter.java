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
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.AppPermissionsPresenter.AppPermissionsView;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.AppUserPermissionsReport;
import stroom.security.shared.PermissionShowLevel;
import stroom.svg.shared.SvgImage;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import javax.inject.Inject;

public class AppPermissionsPresenter
        extends ContentTabPresenter<AppPermissionsView> {

    private final AppUserPermissionsListPresenter appUserPermissionsListPresenter;
    private final AppPermissionsEditPresenter appPermissionsEditPresenter;

    private final SelectionBox<PermissionShowLevel> showLevel = new SelectionBox<>();

    @Inject
    public AppPermissionsPresenter(
            final EventBus eventBus,
            final AppPermissionsView view,
            final AppUserPermissionsListPresenter appUserPermissionsListPresenter,
            final AppPermissionsEditPresenter appPermissionsEditPresenter) {

        super(eventBus, view);
        this.appUserPermissionsListPresenter = appUserPermissionsListPresenter;
        this.appPermissionsEditPresenter = appPermissionsEditPresenter;
        view.setAppUserPermissionListView(appUserPermissionsListPresenter.getView());
        view.setAppPermissionsEditView(appPermissionsEditPresenter.getView());

        showLevel.addItems(PermissionShowLevel.ITEMS);
        showLevel.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        appUserPermissionsListPresenter.getPagerView().addToolbarWidget(showLevel);
        appUserPermissionsListPresenter.setShowLevel(showLevel.getValue());
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(appUserPermissionsListPresenter.getSelectionModel().addSelectionHandler(e -> {
            editPermissions();
        }));
        registerHandler(showLevel.addValueChangeHandler(e -> {
            appUserPermissionsListPresenter.setShowLevel(showLevel.getValue());
            appUserPermissionsListPresenter.refresh();
        }));
        registerHandler(appPermissionsEditPresenter.getSelectionModel().addSelectionHandler(e -> updateDetails()));
        registerHandler(appPermissionsEditPresenter.addValueChangeHandler(e -> {
            if (e.getValue().isRefreshUsers()) {
                refresh();
            }
            if (e.getValue().isRefreshDetails()) {
                updateDetails();
            }
        }));
    }

    private void editPermissions() {
        final AppUserPermissions appUserPermissions = appUserPermissionsListPresenter.getSelectionModel().getSelected();
        if (appUserPermissions != null) {
            appPermissionsEditPresenter.edit(appUserPermissions.getUserRef());
        } else {
            appPermissionsEditPresenter.edit(null);
        }
    }

    public void refresh() {
        appUserPermissionsListPresenter.refresh();
    }

    private void updateDetails() {
        final SafeHtml details = getDetails();
        getView().setDetails(details);
    }

    private SafeHtml getDetails() {
        AppUserPermissionsReport currentPermissions = appPermissionsEditPresenter.getCurrentPermissions();
        final DescriptionBuilder sb = new DescriptionBuilder();
        final AppPermission permission = appPermissionsEditPresenter.getSelectionModel().getSelected();
        if (permission != null) {
            addPaths(
                    currentPermissions.getExplicitPermissions().contains(permission),
                    currentPermissions.getInheritedPermissions().get(permission),
                    sb,
                    "Explicit Permission",
                    "Inherited From:");

            // See if implied by administrator.
            if (!AppPermission.ADMINISTRATOR.equals(permission)) {
                addPaths(
                        currentPermissions
                                .getExplicitPermissions()
                                .contains(AppPermission.ADMINISTRATOR),
                        currentPermissions
                                .getInheritedPermissions()
                                .get(AppPermission.ADMINISTRATOR),
                        sb,
                        "Implied By Administrator",
                        "Implied By Administrator Inherited From:");
            }

            if (sb.toSafeHtml().asString().length() == 0) {
                sb.addTitle("No Permission");
            }
        }

        return sb.toSafeHtml();
    }

    private void addPaths(final boolean direct,
                          final List<String> paths,
                          final DescriptionBuilder sb,
                          final String directTitle,
                          final String inheritedTitle) {
        if (direct) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle(directTitle);
        }

        if (paths != null && paths.size() > 0) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle(inheritedTitle);
            for (final String path : paths) {
                sb.addNewLine();
                sb.addLine(path);
            }
        }
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

        void setAppUserPermissionListView(View view);

        void setAppPermissionsEditView(View view);

        void setDetails(SafeHtml details);
    }
}
