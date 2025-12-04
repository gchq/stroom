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

package stroom.widget.xsdbrowser.client.view;

import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter.XSDBrowserView;
import stroom.widget.xsdbrowser.client.presenter.XSDBrowserUiHandlers;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class XSDBrowserViewImpl extends ViewWithUiHandlers<XSDBrowserUiHandlers> implements XSDBrowserView {

    private final Widget widget;
    @UiField
    Label homeLink;
    @UiField
    Label backLink;
    @UiField
    Label forwardLink;
    @UiField
    HTML documentation;
    @UiField
    XSDConstraintDisplay constraints;
    @UiField
    XSDDisplay diagram;

    @Inject
    public XSDBrowserViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setModel(final XSDModel model) {
        diagram.setModel(model);
    }

    @Override
    public void setSelectedNode(final XSDNode node, final boolean change) {
        diagram.setSelectedNode(node, change);

        if (node != null) {
            documentation.setText(node.getDocumentation());
            constraints.setNode(node);
        } else {
            documentation.setText("");
            constraints.setNode(null);
        }
    }

    @UiHandler(value = "homeLink")
    void handleHomeClick(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().home();
        }
    }

    @UiHandler(value = "backLink")
    void handleBackClick(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().back();
        }
    }

    @UiHandler(value = "forwardLink")
    void handleForwardClick(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().forward();
        }
    }

    public interface Binder extends UiBinder<Widget, XSDBrowserViewImpl> {

    }
}
