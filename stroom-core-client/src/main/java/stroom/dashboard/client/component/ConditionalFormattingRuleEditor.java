/*
 * Copyright 2021 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.dashboard.client.table.cf.ConditionalFormattingSwatchUtil;
import stroom.item.client.SelectionBox;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.TextAttributes;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A conditional formatting rule: the expression choosing the rows it matches, whether it is
/// enabled, whether it hides them, and their style (a standard one or the user's own), bold and
/// italic, with an example.
public class ConditionalFormattingRuleEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup styleGroup;
    @UiField
    FormGroup customStyleGroup;
    @UiField
    SimplePanel expression;
    @UiField
    TickBox enabled;
    @UiField
    TickBox hide;
    @UiField
    SelectionBox<ConditionalFormattingType> formattingType;
    @UiField
    SelectionBox<ConditionalFormattingStyle> formattingStyle;
    @UiField
    TickBox textBold;
    @UiField
    TickBox textItalic;
    @UiField
    SimplePanel example;
    @UiField
    Button editCustomStyle;

    private CustomConditionalFormattingStyle customConditionalFormattingStyle;
    private Runnable onEditCustomStyle = () -> {
    };

    /// Makes the form, with a background style of none.
    public ConditionalFormattingRuleEditor() {
        initWidget(BINDER.createAndBindUi(this));

        formattingType.addItems(ConditionalFormattingType.LIST);
        formattingType.setValue(ConditionalFormattingType.BACKGROUND);

        formattingStyle.setRenderFunction(style -> ConditionalFormattingSwatchUtil
                .createSwatch(formattingType.getValue(), style, getTextAttributes()));
        formattingStyle.addItems(ConditionalFormattingStyle.LIST);
        formattingStyle.setValue(ConditionalFormattingStyle.NONE);
    }

    /// @param expressionEditor The expression editor, a controller of its own.
    public void setExpressionEditor(final IsWidget expressionEditor) {
        expression.setWidget(expressionEditor.asWidget());
    }

    /// @return Whether matching rows are hidden.
    public boolean isHide() {
        return hide.getValue();
    }

    /// @param hide Whether matching rows are hidden.
    public void setHide(final boolean hide) {
        this.hide.setValue(hide);
    }

    /// @param formattingType How the style is applied, or `null` for the background.
    public void setFormattingType(final ConditionalFormattingType formattingType) {
        this.formattingType.setValue(formattingType == null
                ? ConditionalFormattingType.BACKGROUND
                : formattingType);
        updateVisibility();
        updateExampleSwatch();
    }

    /// @return How the style is applied.
    public ConditionalFormattingType getFormattingType() {
        return formattingType.getValue();
    }

    /// @param style The standard style, or `null` for none.
    public void setFormattingStyle(final ConditionalFormattingStyle style) {
        this.formattingStyle.setValue(style == null
                ? ConditionalFormattingStyle.NONE
                : style);
        updateExampleSwatch();
    }

    /// @return The standard style.
    public ConditionalFormattingStyle getFormattingStyle() {
        return formattingStyle.getValue();
    }

    /// @return Whether matching rows' text is bold.
    public boolean isTextBold() {
        return textBold.getValue();
    }

    /// @param bold Whether matching rows' text is bold.
    public void setTextBold(final boolean bold) {
        this.textBold.setValue(bold);
        updateVisibility();
        updateExampleSwatch();
    }

    /// @return Whether matching rows' text is italic.
    public boolean isTextItalic() {
        return textItalic.getValue();
    }

    /// @param italic Whether matching rows' text is italic.
    public void setTextItalic(final boolean italic) {
        this.textItalic.setValue(italic);
        updateVisibility();
        updateExampleSwatch();
    }

    /// @return Whether the rule is enabled.
    public boolean isEnabled() {
        return enabled.getValue();
    }

    /// @param enabled Whether the rule is enabled.
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    /// @param customConditionalFormattingStyle The user's own style, shown in the example.
    public void setCustomConditionalFormattingStyle(final CustomConditionalFormattingStyle
                                                            customConditionalFormattingStyle) {
        this.customConditionalFormattingStyle = customConditionalFormattingStyle;
        updateExampleSwatch();
    }

    /// @param onEditCustomStyle Called when Set Custom Style is pressed.
    public void setOnEditCustomStyle(final Runnable onEditCustomStyle) {
        this.onEditCustomStyle = onEditCustomStyle;
    }

    @UiHandler("formattingType")
    void onFormattingType(final ValueChangeEvent<ConditionalFormattingType> e) {
        updateVisibility();
        updateExampleSwatch();
    }

    @UiHandler("formattingStyle")
    void onFormattingStyle(final ValueChangeEvent<ConditionalFormattingStyle> e) {
        updateExampleSwatch();
    }

    @UiHandler("editCustomStyle")
    void onEditCustomStyle(final ClickEvent e) {
        onEditCustomStyle.run();
    }

    @UiHandler("textBold")
    void onTextBold(final ValueChangeEvent<Boolean> e) {
        updateVisibility();
        updateExampleSwatch();
    }

    @UiHandler("textItalic")
    void onTextItalic(final ValueChangeEvent<Boolean> e) {
        updateVisibility();
        updateExampleSwatch();
    }

    // Draws the styles' swatches again, for the bold and italic chosen, and shows the standard or
    // the custom style for the type
    private void updateVisibility() {
        final ConditionalFormattingStyle style = formattingStyle.getValue();
        formattingStyle.clear();
        formattingStyle.addItems(ConditionalFormattingStyle.LIST);
        formattingStyle.setValue(style == null
                ? ConditionalFormattingStyle.NONE
                : style);

        final ConditionalFormattingType type = formattingType.getValue();
        final boolean custom = type == null || ConditionalFormattingType.CUSTOM.equals(type);
        styleGroup.setVisible(!custom);
        customStyleGroup.setVisible(custom);
    }

    private TextAttributes getTextAttributes() {
        TextAttributes textAttributes = null;
        if (textBold.getValue() || textItalic.getValue()) {
            textAttributes = TextAttributes
                    .builder()
                    .bold(textBold.getValue())
                    .italic(textItalic.getValue())
                    .build();
        }
        return textAttributes;
    }

    private void updateExampleSwatch() {
        example.getElement().setInnerHTML(
                ConditionalFormattingSwatchUtil
                        .createSwatch(
                                formattingType.getValue(),
                                formattingStyle.getValue(),
                                customConditionalFormattingStyle,
                                getTextAttributes())
                        .asString());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ConditionalFormattingRuleEditor> {

    }
}
