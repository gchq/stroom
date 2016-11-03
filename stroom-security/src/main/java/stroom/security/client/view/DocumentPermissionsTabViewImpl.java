/*
 * Copyright 2016 Crown Copyright
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
 */

package stroom.security.client.view;

import stroom.security.client.presenter.DocumentPermissionsTabPresenter;
import stroom.widget.layout.client.view.ResizeSimplePanel;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public final class DocumentPermissionsTabViewImpl extends ViewImpl implements DocumentPermissionsTabPresenter.DocumentPermissionsTabView {
    private final Widget widget;
    @UiField
    ResizeSimplePanel users;
    @UiField
    Label usersLabel;
    @UiField
    ScrollPanel permissions;

    @Inject
    public DocumentPermissionsTabViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setUserView(View view) {
        users.setWidget(view.asWidget());
    }

    @Override
    public void setPermissionsView(View view) {
        permissions.setWidget(view.asWidget());
    }

    @Override
    public void setUsersLabelText(String text) {
        usersLabel.setText(text);
    }

    public interface Binder extends UiBinder<Widget, DocumentPermissionsTabViewImpl> {
    }
}
