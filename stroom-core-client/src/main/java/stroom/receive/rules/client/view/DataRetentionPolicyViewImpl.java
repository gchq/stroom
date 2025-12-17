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

package stroom.receive.rules.client.view;

import stroom.receive.rules.client.presenter.DataRetentionPolicyPresenter.DataRetentionPolicyView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class DataRetentionPolicyViewImpl extends ViewImpl implements DataRetentionPolicyView {

    private final Widget widget;

    @UiField
    SimplePanel table;
    @UiField
    SimplePanel expression;

    @Inject
    public DataRetentionPolicyViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTableView(final View view) {
        this.table.setWidget(view.asWidget());
    }

    @Override
    public void setExpressionView(final View view) {
        this.expression.setWidget(view.asWidget());
    }

    public interface Binder extends UiBinder<Widget, DataRetentionPolicyViewImpl> {

    }
}
