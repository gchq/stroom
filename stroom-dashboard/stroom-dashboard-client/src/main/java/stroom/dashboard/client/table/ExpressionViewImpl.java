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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.ExpressionPresenter.ExpressionView;
import stroom.editor.client.presenter.EditorView;
import stroom.widget.button.client.ImageButton;
import stroom.widget.layout.client.view.ResizeSimplePanel;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ExpressionViewImpl extends ViewWithUiHandlers<ExpressionUiHandlers> implements ExpressionView {
    private static Resources resources;
    private final Widget widget;

    @UiField
    ResizeSimplePanel editorContainer;
    @UiField
    ImageButton addFunction;

    @Inject
    public ExpressionViewImpl(final Binder binder) {
        if (resources == null) {
            resources = GWT.create(Resources.class);
        }

        widget = binder.createAndBindUi(this);

        addFunction.setEnabledImage(resources.expression());

    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setEditor(final EditorView editor) {
        this.editorContainer.setWidget(editor.asWidget());
    }


    @UiHandler("addFunction")
    public void onAddFunctionClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onAddFunction(event);
        }
    }


    public interface Resources extends ClientBundle {
        ImageResource expression();
    }

    public interface Binder extends UiBinder<Widget, ExpressionViewImpl> {
    }
}
