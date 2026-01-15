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

package stroom.widget.xsdbrowser.client.presenter;

import stroom.widget.xsdbrowser.client.view.XSDModel;
import stroom.widget.xsdbrowser.client.view.XSDNode;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class XSDBrowserPresenter
        extends MyPresenterWidget<XSDBrowserPresenter.XSDBrowserView>
        implements XSDBrowserUiHandlers {

    private XSDModel model;

    @Inject
    public XSDBrowserPresenter(final EventBus eventBus, final XSDBrowserView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void setModel(final XSDModel model) {
        this.model = model;

        registerHandler(model.addDataSelectionHandler(event ->
                getView().setSelectedNode(event.getSelectedItem(), event.isDoubleSelect())));

        getView().setModel(model);
    }

    @Override
    public void back() {
        model.back();
    }

    @Override
    public void forward() {
        model.forward();
    }

    @Override
    public void home() {
        model.home();
    }

    public interface XSDBrowserView extends View, HasUiHandlers<XSDBrowserUiHandlers> {

        void setModel(XSDModel model);

        void setSelectedNode(XSDNode node, boolean change);
    }
}
