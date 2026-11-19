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

package stroom.about.client.component;

import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Component;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The About dialog's content: Stroom's logo, the configured About text, and the build and node
/// it is running on.
public class About extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DivElement logo;
    @UiField
    DivElement html;
    @UiField
    SpanElement buildVersion;
    @UiField
    SpanElement buildDate;
    @UiField
    SpanElement upDate;
    @UiField
    SpanElement nodeName;

    /// Makes an empty About, with Stroom's logo.
    public About() {
        initWidget(BINDER.createAndBindUi(this));
        // No widget a template can declare shows an SvgImage yet
        logo.setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.LOGO));
    }

    /// @param aboutHtml The About text configured for this Stroom, as HTML.
    public void setAboutHtml(final String aboutHtml) {
        html.setInnerHTML(aboutHtml);
    }

    /// @param buildVersion The version of Stroom running.
    public void setBuildVersion(final String buildVersion) {
        this.buildVersion.setInnerText(buildVersion);
    }

    /// @param buildDate When it was built, formatted.
    public void setBuildDate(final String buildDate) {
        this.buildDate.setInnerText(buildDate);
    }

    /// @param upDate When it started, formatted.
    public void setUpDate(final String upDate) {
        this.upDate.setInnerText(upDate);
    }

    /// @param nodeName The name of the node that answered.
    public void setNodeName(final String nodeName) {
        this.nodeName.setInnerText(nodeName);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, About> {

    }
}
