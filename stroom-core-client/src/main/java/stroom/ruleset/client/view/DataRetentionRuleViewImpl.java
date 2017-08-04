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

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.item.client.ItemListBox;
import stroom.ruleset.client.presenter.DataRetentionRulePresenter.DataRetentionRuleView;
import stroom.streamstore.shared.TimeUnit;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

public class DataRetentionRuleViewImpl extends ViewImpl implements DataRetentionRuleView {
    private final Widget widget;

    @UiField
    SimplePanel expression;
    @UiField
    TextBox name;
    @UiField
    TickBox forever;
    @UiField
    Label retainLabel;
    @UiField
    ValueSpinner age;
    @UiField
    ItemListBox<TimeUnit> timeUnit;

    @Inject
    public DataRetentionRuleViewImpl(final DataRetentionRuleViewImpl.Binder binder) {
        widget = binder.createAndBindUi(this);

        age.setMin(1);
        age.setMax(100);
        age.setValue(1);

        timeUnit.addItem(TimeUnit.MINUTES);
        timeUnit.addItem(TimeUnit.HOURS);
        timeUnit.addItem(TimeUnit.DAYS);
        timeUnit.addItem(TimeUnit.WEEKS);
        timeUnit.addItem(TimeUnit.MONTHS);
        timeUnit.addItem(TimeUnit.YEARS);
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
    public boolean isForever() {
        return forever.getBooleanValue();
    }

    @Override
    public void setForever(final boolean forever) {
        this.forever.setBooleanValue(forever);
        setEnabled(!forever);
    }

    @Override
    public int getAge() {
        return age.getValue();
    }

    @Override
    public void setAge(final int age) {
        this.age.setValue(age);
    }

    @Override
    public TimeUnit getTimeUnit() {
        return timeUnit.getSelectedItem();
    }

    @Override
    public void setTimeUnit(final TimeUnit timeUnit) {
        this.timeUnit.setSelectedItem(timeUnit);
    }

    @UiHandler("forever")
    public void onAddFunctionClick(final ValueChangeEvent<TickBoxState> event) {
        setEnabled(!isForever());
    }

    private void setEnabled(final boolean enabled) {
        if (enabled) {
            retainLabel.getElement().getStyle().setOpacity(1);
        } else {
            retainLabel.getElement().getStyle().setOpacity(0.5);
        }
        age.setEnabled(enabled);
        timeUnit.setEnabled(enabled);
    }

    public interface Binder extends UiBinder<Widget, DataRetentionRuleViewImpl> {
    }
}
