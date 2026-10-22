/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AiToolPoliciesPresenter.AiToolPoliciesView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

/// The MCP Tool Policies screen: the list of tool policies.
public class AiToolPoliciesViewImpl extends ViewImpl implements AiToolPoliciesView {

    private final Widget widget;

    @UiField
    SimplePanel listContainer;

    /// @param binder Binds the view's fields to its UiBinder template.
    @Inject
    public AiToolPoliciesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setList(final View view) {
        listContainer.setWidget(view.asWidget());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    // --------------------------------------------------------------------------------


    /// Binds the view's fields to its UiBinder template.
    public interface Binder extends UiBinder<Widget, AiToolPoliciesViewImpl> {

    }
}
