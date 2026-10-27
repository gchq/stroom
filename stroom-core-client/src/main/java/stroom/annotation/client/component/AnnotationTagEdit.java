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

import stroom.dashboard.client.table.cf.ConditionalFormattingSwatchUtil;
import stroom.item.client.SelectionBox;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ConditionalFormattingType;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

/// An annotation tag: its name, its text (for a standard comment) or style (for a label), and a
/// button that opens its permissions.
public class AnnotationTagEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    FormGroup tagTextGroup;
    @UiField
    TextArea tagText;
    @UiField
    FormGroup styleGroup;
    @UiField
    SelectionBox<ConditionalFormattingStyle> formattingStyle;
    @UiField
    Button editPermissions;

    private Runnable onPermissions = () -> {
    };

    /// Makes an empty form, offering every style, each shown as a swatch, None chosen.
    public AnnotationTagEdit() {
        initWidget(BINDER.createAndBindUi(this));
        formattingStyle.setRenderFunction(style -> ConditionalFormattingSwatchUtil
                .createSwatch(ConditionalFormattingType.BACKGROUND, style, null));
        formattingStyle.addItems(ConditionalFormattingStyle.LIST);
        formattingStyle.setValue(ConditionalFormattingStyle.NONE);
    }

    /// Puts the focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The tag's name.
    public String getName() {
        return name.getText();
    }

    /// @param name The tag's name.
    public void setName(final String name) {
        this.name.setText(name);
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

    /// @param tagText The tag's text.
    public void setTagText(final String tagText) {
        this.tagText.setText(tagText);
    }

    /// @param show Whether the style is shown, as it is for a label.
    public void showStyle(final boolean show) {
        styleGroup.setVisible(show);
    }

    /// @return The style chosen.
    public ConditionalFormattingStyle getStyle() {
        return formattingStyle.getValue();
    }

    /// @param style The style chosen.
    public void setStyle(final ConditionalFormattingStyle style) {
        formattingStyle.setValue(style);
    }

    /// @param onPermissions Called when Change Permissions is pressed.
    public void setOnPermissions(final Runnable onPermissions) {
        this.onPermissions = onPermissions;
    }

    @UiHandler("editPermissions")
    void onEditPermissions(final ClickEvent e) {
        onPermissions.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnnotationTagEdit> {

    }
}
