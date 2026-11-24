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

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.BasicTextInputSettingsPresenter.BasicTextInputSettingsView;
import stroom.dashboard.shared.TextInputFormat;
import stroom.item.client.SelectionBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class BasicTextInputSettingsViewImpl extends ViewImpl implements BasicTextInputSettingsView {

    private final Widget widget;
    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    TextBox key;
    @UiField
    SelectionBox<TextInputFormat> format;
    @UiField
    TextBox pattern;

    @Inject
    public BasicTextInputSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        format.addItems(TextInputFormat.values());
        format.setValue(TextInputFormat.TEXT);
        format.addValueChangeHandler(event -> updatePatternEnabled());
        updatePatternEnabled();
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
    public TextInputFormat getFormat() {
        return format.getValue();
    }

    @Override
    public void setFormat(final TextInputFormat format) {
        this.format.setValue(format);
        updatePatternEnabled();
    }

    @Override
    public String getPattern() {
        // Use null for no pattern so an untouched setting isn't seen as a change.
        final String value = pattern.getValue();
        return value.isEmpty()
                ? null
                : value;
    }

    @Override
    public void setPattern(final String pattern) {
        this.pattern.setValue(pattern);
    }

    private void updatePatternEnabled() {
        pattern.setEnabled(format.getValue() == TextInputFormat.PATTERN);
    }

    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    public interface Binder extends UiBinder<Widget, BasicTextInputSettingsViewImpl> {

    }
}
