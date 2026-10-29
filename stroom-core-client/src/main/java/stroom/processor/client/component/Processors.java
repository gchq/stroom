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

import stroom.query.client.component.ExpressionTree;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A pipeline's or analytic rule's Processors tab: the processors and their filters (a list its
/// controller shows here) above, and the selected one's details and filter expression (shown here
/// by its controller) below.
public class Processors extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ProcessorList processorList;
    @UiField
    Html info;
    @UiField
    ExpressionTree expressionTree;

    /// Makes the tab, with no list until it is set, and no expression shown.
    public Processors() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The processors and their filters, which its controller attaches the list's controller
    /// to.
    public ProcessorList getProcessorList() {
        return processorList;
    }

    /// @param info The selected processor's or filter's details.
    public void setInfo(final SafeHtml info) {
        this.info.setHTML(info);
    }

    /// @return The expression's tree, which its controller attaches the tree's controller to.
    public ExpressionTree getExpressionTree() {
        return expressionTree;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, Processors> {

    }
}
