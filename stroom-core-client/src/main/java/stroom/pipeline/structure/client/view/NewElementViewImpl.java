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

import stroom.pipeline.structure.client.presenter.NewElementPresenter.NewElementView;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class NewElementViewImpl extends ViewImpl implements NewElementView {

    private final Widget widget;
    @UiField
    TextBox name;
    @UiField
    TextArea description;

    @Inject
    public NewElementViewImpl(final Binder binder) {
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
    public HasKeyDownHandlers getNameBox() {
        return name;
    }

    @Override
    public HasText getName() {
        return name;
    }

    @Override
    public HasText getDescription() {
        return description;
    }

    @Override
    public HasKeyDownHandlers getDescriptionBox() {
        return description;
    }

    public interface Binder extends UiBinder<Widget, NewElementViewImpl> {

    }
}
