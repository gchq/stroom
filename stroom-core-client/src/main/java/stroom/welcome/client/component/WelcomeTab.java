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

package stroom.welcome.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The Welcome screen: the site's welcome text, then who the user is and which Stroom this is.
public class WelcomeTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel html;
    @UiField
    Grid buildInfo;
    @UiField
    Label userIdentity;
    @UiField
    Label displayName;
    @UiField
    Label fullName;
    @UiField
    Label buildVersion;
    @UiField
    Label buildDate;
    @UiField
    Label upDate;
    @UiField
    Label nodeName;

    /// Makes the screen, with no content until it is given it.
    public WelcomeTab() {
        initWidget(BINDER.createAndBindUi(this));
        // Each row is a name and its value: the name cells are row headers, so a screen reader
        // reads the name with its value
        for (int row = 0; row < buildInfo.getRowCount(); row++) {
            buildInfo.getCellFormatter().getElement(row, 0).setAttribute("role", "rowheader");
        }
    }

    /// @param html The site's welcome text, as HTML set by the administrator.
    public void setWelcomeHtml(final String html) {
        this.html.getElement().setInnerHTML(html);
    }

    /// Shows who the user is.
    ///
    /// @param subjectId   Their unique identity.
    /// @param displayName The name shown for them.
    /// @param fullName    Their full name.
    public void setUser(final String subjectId, final String displayName, final String fullName) {
        userIdentity.setText(subjectId);
        this.displayName.setText(displayName);
        this.fullName.setText(fullName);
    }

    /// Shows which Stroom this is.
    ///
    /// @param buildVersion Its version.
    /// @param buildDate    When it was built.
    /// @param upDate       When it started.
    /// @param nodeName     The node the user is on.
    public void setBuild(final String buildVersion,
                         final String buildDate,
                         final String upDate,
                         final String nodeName) {
        this.buildVersion.setText(buildVersion);
        this.buildDate.setText(buildDate);
        this.upDate.setText(upDate);
        this.nodeName.setText(nodeName);
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, WelcomeTab> {

    }
}
