/*
 * Copyright 2026 Crown Copyright
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

package stroom.annotation.client.component;

import stroom.explorer.client.component.FindPanel;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The content of Choose Annotation, the Annotations tab and an annotation's Link To and Link From
/// tabs: a filter ([FindPanel]) above the annotations found ([FindAnnotationList]).
public class FindAnnotations extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FindPanel findPanel;
    @UiField
    FindAnnotationList resultList;

    /// Makes the content, with nothing found.
    public FindAnnotations() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The filter and what it finds.
    public FindPanel getFindPanel() {
        return findPanel;
    }

    /// @return The annotations found, which its controller attaches the list's controller to.
    public FindAnnotationList getResultList() {
        return resultList;
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, FindAnnotations> {

    }
}
