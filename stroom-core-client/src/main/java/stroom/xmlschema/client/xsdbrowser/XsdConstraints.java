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

package stroom.xmlschema.client.xsdbrowser;

import stroom.widget.text.client.Label;
import stroom.xmlschema.client.xsdbrowser.XsdNode.XsdType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The restriction of the selected node's type: its enumeration, or failing that its pattern, under
/// a heading naming which. Its markup is `XsdConstraints.ui.xml`; only the values are added in
/// Java.
public class XsdConstraints extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel restriction;
    @UiField
    Label restrictionType;
    @UiField
    FlowPanel values;

    /// Makes it showing nothing, until [#setNode(XsdNode)] gives it a node.
    public XsdConstraints() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows the restriction of a node's type (an element's or attribute's own type), or nothing if
    /// it has none.
    ///
    /// @param node The node, or null for none.
    public void setNode(final XsdNode node) {
        values.clear();
        restriction.setVisible(false);

        if (node != null) {
            XsdNode typeNode = node;
            if (node.getType() == XsdType.ATTRIBUTE || node.getType() == XsdType.ELEMENT) {
                typeNode = node.getTypeNode();
            }

            final XsdConstraint constraints = new XsdConstraint(typeNode);
            if (!showList(constraints.getEnumerationList(), "Enumeration")) {
                showList(constraints.getPatternList(), "Pattern");
            }
        }
    }

    // Shows the restriction's values under a heading naming its kind, if it has any
    private boolean showList(final List<String> list, final String type) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        restrictionType.setText(type);
        for (final String value : list) {
            // Kept on one line (xsd-browser.css)
            final Label label = new Label(value);
            label.addStyleName("xsd-browser__one-line");
            values.add(label);
        }
        restriction.setVisible(true);
        return true;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, XsdConstraints> {

    }
}
