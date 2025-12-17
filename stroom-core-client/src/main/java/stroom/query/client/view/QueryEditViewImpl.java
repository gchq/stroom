/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.client.view;

import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class QueryEditViewImpl
        extends ViewImpl
        implements QueryEditView {

    private final Widget widget;

    @UiField
    SimplePanel queryHelpContainer;
    @UiField
    SimplePanel queryEditorContainer;
    @UiField
    SimplePanel resultContainer;

    @Inject
    public QueryEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setQueryHelp(final View view) {
        queryHelpContainer.setWidget(view.asWidget());
    }

    @Override
    public void setQueryEditor(final View view) {
        view.asWidget().addStyleName("dashboard-panel overflow-hidden");
        queryEditorContainer.setWidget(view.asWidget());
    }

    @Override
    public void setResultView(final View view) {
        resultContainer.setWidget(view.asWidget());
    }

    public interface Binder extends UiBinder<Widget, QueryEditViewImpl> {

    }
}
