/*
 * Copyright 2022 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A streaming analytic rule's processing: its processors and their filters (shown here by its
/// controller).
public class StreamingProcessing extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel processors;

    /// Makes it, with no processors until they are set.
    public StreamingProcessing() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param processors The rule's processors and their filters.
    public void setProcessors(final IsWidget processors) {
        this.processors.setWidget(processors);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, StreamingProcessing> {

    }
}
