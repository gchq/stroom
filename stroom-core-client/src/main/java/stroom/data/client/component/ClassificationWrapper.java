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

package stroom.data.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiChild;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// Some content (e.g. the data viewer's) with its data's classification in a banner below it. Its
/// owner attaches a [stroom.data.client.component.ClassificationLabelController] to the banner
/// ([#getClassificationLabel()]) for its colours. Its markup is `ClassificationWrapper.ui.xml`.
public class ClassificationWrapper extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel content;
    @UiField
    ClassificationLabel classification;

    /// Makes a wrapper with no content and no classification.
    public ClassificationWrapper() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param content What is shown above the classification.
    public void setContent(final IsWidget content) {
        this.content.setWidget(content);
    }

    /// Sets what is shown above the classification, from a template's `<d:content>` child.
    ///
    /// @param content What is shown above the classification.
    @UiChild(tagname = "content", limit = 1)
    public void addContent(final Widget content) {
        setContent(content);
    }

    /// @param classification The data's classification, or null for none.
    public void setClassification(final String classification) {
        this.classification.setClassification(classification);
    }

    /// @return The classification banner, for a controller to give its colours.
    public ClassificationLabel getClassificationLabel() {
        return classification;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ClassificationWrapper> {

    }
}
