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

package stroom.ruleset.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.item.client.ItemListBox;
import stroom.ruleset.client.presenter.RulePresenter.RuleView;
import stroom.ruleset.shared.DataReceiptAction;

public class RuleViewImpl extends ViewImpl implements RuleView {
    private final Widget widget;

    @UiField
    SimplePanel expression;
    @UiField
    TextBox name;
    @UiField
    ItemListBox<DataReceiptAction> action;

    @Inject
    public RuleViewImpl(final RuleViewImpl.Binder binder) {
        widget = binder.createAndBindUi(this);

        action.addItem(DataReceiptAction.RECEIVE);
        action.addItem(DataReceiptAction.REJECT);
        action.addItem(DataReceiptAction.DROP);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setExpressionView(final View view) {
        expression.setWidget(view.asWidget());
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public DataReceiptAction getAction() {
        return action.getSelectedItem();
    }

    @Override
    public void setAction(final DataReceiptAction action) {
        this.action.setSelectedItem(action);
    }

    public interface Binder extends UiBinder<Widget, RuleViewImpl> {
    }
}
