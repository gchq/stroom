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

package stroom.widget.xsdbrowser.client.view;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MaxScrollPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import stroom.widget.xsdbrowser.client.view.XSDNode.XSDType;

import java.util.List;

public class XSDConstraintDisplay extends Composite {
    private final ScrollPanel layout;

    public XSDConstraintDisplay() {
        layout = new MaxScrollPanel();
        initWidget(layout);
    }

    public void setNode(final XSDNode node) {
        layout.clear();

        if (node != null) {
            XSDNode typeNode = node;
            if (node.getType() == XSDType.ATTRIBUTE || node.getType() == XSDType.ELEMENT) {
                typeNode = node.getTypeNode();
            }

            final XSDConstraint constraints = new XSDConstraint(typeNode);
            SimplePanel list = getList(constraints.getEnumerationList(), true);
            if (list == null) {
                list = getList(constraints.getPatternList(), true);
            }
            if (list != null) {
                layout.setWidget(list);
            }
        }
    }

    private SimplePanel getList(final List<String> list, final boolean enumeration) {
        if (list != null && list.size() > 0) {
            Label lblRestrictionType = null;

            if (enumeration) {
                lblRestrictionType = new Label("Enumeration", false);
            } else {
                lblRestrictionType = new Label("Pattern", false);
            }
            lblRestrictionType.addStyleName("restrictionType");

            final VerticalPanel layout = new VerticalPanel();
            layout.add(lblRestrictionType);

            for (final String value : list) {
                layout.add(new Label(value, false));
            }

            final SimplePanel simplePanel = new SimplePanel();
            simplePanel.addStyleName("padding");
            simplePanel.add(layout);

            return simplePanel;
        }

        return null;
    }
}
