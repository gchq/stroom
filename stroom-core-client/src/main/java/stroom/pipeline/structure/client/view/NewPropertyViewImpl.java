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

package stroom.pipeline.structure.client.view;

import stroom.item.client.SelectionBox;
import stroom.pipeline.structure.client.presenter.NewPropertyPresenter.NewPropertyView;
import stroom.pipeline.structure.client.presenter.PropertyListPresenter.Source;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class NewPropertyViewImpl
        extends ViewWithUiHandlers<NewPropertyUiHandlers>
        implements NewPropertyView {

    private final Widget widget;

    @UiField
    Label element;
    @UiField
    Label name;
    @UiField
    Label description;
    @UiField
    FormGroup defaultValueGroup;
    @UiField
    Label defaultValue;
    @UiField
    FormGroup inheritedValueGroup;
    @UiField
    Label inheritedValue;
    @UiField
    SelectionBox<Source> source;
    @UiField
    SimplePanel value;

    @Inject
    public NewPropertyViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        defaultValueGroup.setVisible(false);
        inheritedValueGroup.setVisible(false);
        source.addItem(Source.DEFAULT);
        source.addItem(Source.INHERIT);
        source.addItem(Source.LOCAL);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        source.focus();
    }

    @Override
    public void setElement(final String element) {
        this.element.setText(element);
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void setDescription(final String description) {
        this.description.setText(description);
    }

    @Override
    public void setDefaultValue(final String defaultValue) {
        if (defaultValue == null || defaultValue.length() == 0) {
            defaultValueGroup.setVisible(false);
        } else {
            defaultValueGroup.setVisible(true);
            this.defaultValue.setText(defaultValue);
        }
    }

    @Override
    public void setInherited(final String inheritedFrom, final String inheritedValue) {
        if (inheritedValue == null || inheritedValue.length() == 0) {
            inheritedValueGroup.setVisible(false);
        } else {
            inheritedValueGroup.setVisible(true);
            inheritedValueGroup.setLabel("Inherited Value From '" + inheritedFrom + "'");
            this.inheritedValue.setText(inheritedValue);
        }
    }

    @Override
    public Source getSource() {
        return this.source.getValue();
    }

    @Override
    public void setSource(final Source source) {
        this.source.setValue(source);
    }

    @Override
    public void setValueWidget(final Widget widget) {
        value.setWidget(widget);
    }

    @UiHandler("source")
    void onSourceValueChange(final ValueChangeEvent<Source> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSourceChange(source.getValue());
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, NewPropertyViewImpl> {

    }
}
