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

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.BasicListInputSettingsPresenter.BasicListInputSettingsView;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BasicListInputSettingsViewImpl extends ViewImpl implements BasicListInputSettingsView {

    private final Widget widget;
    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    TextBox key;
    @UiField
    TextArea values;
    @UiField
    CustomCheckBox useDictionary;
    @UiField
    SimplePanel dictionary;
    @UiField
    CustomCheckBox allowTextEntry;

    @Inject
    public BasicListInputSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public void setId(final String id) {
        this.id.setText(id);
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
    public String getKey() {
        return key.getValue();
    }

    @Override
    public void setKey(final String key) {
        this.key.setValue(key);
    }

    @Override
    public List<String> getValues() {
        return Arrays.asList(values.getValue().split("\n"));
    }

    @Override
    public void setValues(final List<String> values) {
        if (values != null) {
            this.values.setValue(values.stream().collect(Collectors.joining("\n")));
        }
    }

    @Override
    public boolean isUseDictionary() {
        return useDictionary.getValue();
    }

    @Override
    public void setUseDictionary(final boolean useDictionary) {
        this.useDictionary.setValue(useDictionary);
    }

    @Override
    public void setDictionaryView(final View view) {
        dictionary.setWidget(view.asWidget());
    }

    @Override
    public boolean isAllowTextEntry() {
        return allowTextEntry.getValue();
    }

    @Override
    public void setAllowTextEntry(final boolean allowTextEntry) {
        this.allowTextEntry.setValue(allowTextEntry);
    }

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    public interface Binder extends UiBinder<Widget, BasicListInputSettingsViewImpl> {

    }
}
