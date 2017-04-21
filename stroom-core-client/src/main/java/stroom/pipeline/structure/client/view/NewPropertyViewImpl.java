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

package stroom.pipeline.structure.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.item.client.ItemListBox;
import stroom.pipeline.structure.client.presenter.NewPropertyPresenter.NewPropertyView;
import stroom.pipeline.structure.client.presenter.PropertyListPresenter.Source;

public class NewPropertyViewImpl extends ViewImpl implements NewPropertyView {
    public interface Binder extends UiBinder<Widget, NewPropertyViewImpl> {
    }

    private final Widget widget;

    @UiField
    Label element;
    @UiField
    Label name;
    @UiField
    ItemListBox<Source> source;
    @UiField
    SimplePanel value;

    @Inject
    public NewPropertyViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        source.addItems(Source.values());
    }

    @Override
    public Widget asWidget() {
        return widget;
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
    public ItemListBox<Source> getSource() {
        return source;
    }

    @Override
    public void setValueWidget(final Widget widget) {
        value.setWidget(widget);
    }
}
