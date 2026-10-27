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

package stroom.processor.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A pipeline's or analytic rule's Processors tab: the processors and their filters (a list its
/// controller shows here) above, and the selected one's details and filter expression (shown here
/// by its controller) below.
public class Processors extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel processorList;
    @UiField
    HTML info;
    @UiField
    SimplePanel details;

    /// Makes the tab, with no list or expression until they are set.
    public Processors() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param processorList The processors and their filters.
    public void setProcessorList(final IsWidget processorList) {
        this.processorList.setWidget(processorList);
    }

    /// @param info The selected processor's or filter's details.
    public void setInfo(final SafeHtml info) {
        this.info.setHTML(info);
    }

    /// @param details The selected filter's expression.
    public void setDetails(final IsWidget details) {
        this.details.setWidget(details);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, Processors> {

    }
}
