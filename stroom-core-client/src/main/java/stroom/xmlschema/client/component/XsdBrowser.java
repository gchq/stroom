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

package stroom.xmlschema.client.component;

import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Html;
import stroom.xmlschema.client.xsdbrowser.XsdConstraints;
import stroom.xmlschema.client.xsdbrowser.XsdDiagram;
import stroom.xmlschema.client.xsdbrowser.XsdModel;
import stroom.xmlschema.client.xsdbrowser.XsdNode;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A schema's diagram, with Home, Back and Forward to move around it, and the documentation and
/// constraints of the node selected in it.
public class XsdBrowser extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    LinkButton homeLink;
    @UiField
    LinkButton backLink;
    @UiField
    LinkButton forwardLink;
    @UiField
    Html documentation;
    @UiField
    XsdConstraints constraints;
    @UiField
    XsdDiagram diagram;

    /// Makes an empty browser.
    public XsdBrowser() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param model The schema to show.
    public void setModel(final XsdModel model) {
        diagram.setModel(model);
    }

    /// Shows a node selected, with its documentation and constraints.
    ///
    /// @param node   The node, or null for none.
    /// @param change Whether the diagram moves to it, e.g. on a double click.
    public void setSelectedNode(final XsdNode node, final boolean change) {
        diagram.setSelectedNode(node, change);

        if (node != null) {
            documentation.setText(node.getDocumentation());
            constraints.setNode(node);
        } else {
            documentation.setText("");
            constraints.setNode(null);
        }
    }

    /// @param onHome What to run when the user presses Home.
    public void setOnHome(final Runnable onHome) {
        homeLink.setOnClick(onHome);
    }

    /// @param onBack What to run when the user presses Back.
    public void setOnBack(final Runnable onBack) {
        backLink.setOnClick(onBack);
    }

    /// @param onForward What to run when the user presses Forward.
    public void setOnForward(final Runnable onForward) {
        forwardLink.setOnClick(onForward);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, XsdBrowser> {

    }
}
