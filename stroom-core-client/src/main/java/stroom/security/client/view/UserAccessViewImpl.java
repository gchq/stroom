/*
 * Copyright 2023 Crown Copyright
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

import stroom.security.client.presenter.UserAccessPresenter.UserAccessView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class UserAccessViewImpl extends ViewImpl implements UserAccessView {

    private final Widget widget;

    @UiField
    SimplePanel listContainer;
    @UiField
    SimplePanel sessionsContainer;

    @Inject
    public UserAccessViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
    }

    @Override
    public void focus() {
        // The quick filter inside the list view takes focus itself.
    }

    @Override
    public void setList(final View view) {
        listContainer.setWidget(view.asWidget());
    }

    @Override
    public void setSessionList(final View view) {
        sessionsContainer.setWidget(view.asWidget());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, UserAccessViewImpl> {

    }
}
