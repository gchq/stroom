/*
 * Copyright 2025 Crown Copyright
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

import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A new annotation tag: its name and, for a standard comment, its text.
public class AnnotationTagCreate extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    FormGroup tagTextGroup;
    @UiField
    TextArea tagText;

    /// Makes an empty form.
    public AnnotationTagCreate() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The tag's name.
    public String getName() {
        return name.getText();
    }

    /// Shows the tag's text, for a tag type that has one.
    ///
    /// @param show  Whether the tag type has text.
    /// @param label The text's label, the tag type's name.
    public void showTagText(final boolean show, final String label) {
        tagTextGroup.setLabel(label);
        tagTextGroup.setVisible(show);
    }

    /// @return The tag's text.
    public String getTagText() {
        return tagText.getText();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnnotationTagCreate> {

    }
}
