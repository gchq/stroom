/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.widget.form.client;

import stroom.util.shared.NullSafe;
import stroom.widget.help.client.HelpButton;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

/**
 * Composite to show a labelled form field or group of form fields.
 * <p>
 * To add a help button to the right of the label you have two options:
 * <p>
 * <p/>
 * For simple plain text help use the {@code helpText} attr (any HTML will be escaped):
 * <pre>{@code
 * <form:FormGroup ... helpText="This is my help text">
 * }</pre>
 * <p/>
 * <p>
 * For rich HTML help text then add a <strong>single</strong> {@link HelpHTML} element
 * containing the HTML help content:
 * <pre>{@code
 * <form:FormGroup ...>
 *     <form:HelpHTML>
 *         <p>This is para 1</p>
 *         <p>This is some <code>code</code></p>
 *     </form:HelpHTML>
 *     <g:TextBox ui:field="textBox" addStyleNames="w-100"/>
 * </form:FormGroup>
 * }</pre>
 * <p>If you have both helpText and a HelpHTML element, then helpText will be used.</p>
 * <p>If you want to programmatically set the help text then use
 * {@link FormGroup#overrideHelpText(SafeHtml)}</p>
 * <p>
 * {@code formLabel} will be automatically added as a {@code <h4>} heading
 * at the top of the help popup.
 * </p>
 * <p/>
 * <p>
 * To add descriptive text below the label (that is always visible, unlike the help),
 * use a <strong>single</strong> {@link DescriptionHTML} element like this:
 * <pre>{@code
 * <form:FormGroup ...>
 *     <form:DescriptionHTML>
 *         <p>This is para 1</p>
 *         <p>This is some <code>code</code></p>
 *     </form:DescriptionHTML>
 *     <g:TextBox ui:field="textBox" addStyleNames="w-100"/>
 * </form:FormGroup>
 * }</pre>
 * </p>
 */
public class FormGroup extends Composite implements HasWidgets {

    public static final String CLASS_NAME_FORM_GROUP_HELP = "form-group-help";
    public static final String STYLE_FORM_GROUP_DESCRIPTION_CONTAINER = "form-group-description-container";
    public static final String STYLE_FORM_GROUP_DESCRIPTION_CONTAINER_DISABLED =
            STYLE_FORM_GROUP_DESCRIPTION_CONTAINER + "--disabled";

    private final FlowPanel formGroupPanel = new FlowPanel();
    private final FormLabel formLabel = new FormLabel();
    private final HelpButton helpButton = HelpButton.create();
    private final FlowPanel labelPanel = new FlowPanel();
    private final FlowPanel descriptionPanel = new FlowPanel();
    private final Label feedbackLabel = new Label();

    private String id;
    private Widget childWidget = null;
    // Set by the presence of a <HelpHTML> element in the ui.xml
    private HelpHTML helpHTML = null;
    // The plain help text bound to the attribute in the ui.xml, or set programmatically.
    // Trumps helpHTML
    private String helpText = null;
    // HTML help text set programmatically, that overrides the other two
    // Trumps helpText and helpHTML
    private SafeHtml helpTextOverride = null;
    private DescriptionHTML descriptionHTML = null;
    private boolean disabled = false;

    public FormGroup() {
        feedbackLabel.setStyleName("invalid-feedback");
        formGroupPanel.addStyleName("form-group");
        labelPanel.addStyleName("form-group-label-container");
        formLabel.addStyleName("form-group-label");
        descriptionPanel.addStyleName(STYLE_FORM_GROUP_DESCRIPTION_CONTAINER);
        helpButton.addStyleName("form-group-help");

        // Don't want the user to have to tab over each help btn when you can
        // call up the help with F1
        helpButton.preventTabFocus();
        updateLabelPanel();

        initWidget(formGroupPanel);

        formGroupPanel.addDomHandler(
                event ->
                        handleKeyEvent(event.getNativeEvent()),
                KeyDownEvent.getType());
    }

    private void handleKeyEvent(final NativeEvent nativeEvent) {
        if (Action.HELP == KeyBinding.test(nativeEvent)) {
            helpButton.showHelpPopup();
            nativeEvent.preventDefault();
        }
    }

    public void setIdentity(final String id) {
        this.id = id;
        formLabel.setIdentity(id);
        if (childWidget != null) {
            childWidget.getElement().setId(id);
        }
    }

    public void setLabel(final String label) {
        if (!Objects.equals(getLabel(), label)) {
            formLabel.setLabel(label);

            if (NullSafe.isBlankString(label)) {
                helpButton.setTitle("Click for help");
            } else {
                helpButton.setTitle(label + " - Click for help");
                helpButton.setHelpContentHeading(label);
            }
            updateLabelPanel();
        }
    }

    public String getLabel() {
        return formLabel.getLabel();
    }

    /**
     * If disabled, the {@link FormGroup} label and descriptionHtml text will be greyed out
     * to show the group as being disabled. This helps when it is not easy to see that
     * the control in the group is disabled.
     */
    public void setDisabled(final boolean disabled) {
        this.disabled = disabled;
        formLabel.setDisabled(disabled);
        if (disabled) {
            descriptionPanel.addStyleName(STYLE_FORM_GROUP_DESCRIPTION_CONTAINER_DISABLED);
        } else {
            descriptionPanel.removeStyleName(STYLE_FORM_GROUP_DESCRIPTION_CONTAINER_DISABLED);
        }
    }

    public boolean isDisabled() {
        return disabled;
    }

    /**
     * Plain text, any HTML will be escaped.
     * The helpText attr trumps the {@code <form:HelpHTML>} element.
     * <p>
     * To programmatically set HTML help text, use {@link FormGroup#overrideHelpText(SafeHtml)}.
     * </p>
     */
    @SuppressWarnings("unused") // Bound to UI attr
    public void setHelpText(final String helpText) {
        this.helpText = helpText;

//        // This allows us to have hard coded help in the ui.xml but override it
//        // using helpText, or set helpText back to null to use the hardcoded
//        // ui.xml content
//        if (NullSafe.isBlankString(helpText) && helpHTML != null) {
//            this.helpText = helpHTML.getHTML();
//        }
        updateHelpButton();
    }

    public void overrideHelpText(final SafeHtml helpTextOverride) {
        this.helpTextOverride = helpTextOverride;
        updateHelpButton();
    }

    private String getHelpText() {
        return helpText;
    }

    private SafeHtml getHelpTextOverride() {
        return helpTextOverride;
    }

    private HelpHTML getHelpHTML() {
        return helpHTML;
    }

    private void updateHelpButton() {
        final String plainHelpText = getHelpText();
        final SafeHtml helpTextOverride = getHelpTextOverride();
        final HelpHTML helpHTML = getHelpHTML();

        final SafeHtml effectiveHelpText;
//        final boolean haveHelpText;

        if (helpTextOverride != null) {
//            haveHelpText = true;
            effectiveHelpText = helpTextOverride;
        } else if (NullSafe.isNonBlankString(plainHelpText)) {
//            haveHelpText = true;
            // Escape any html in there, wrap it in a para so styling is consistent
            effectiveHelpText = HtmlBuilder.builder()
                    .para(paraBuilder -> paraBuilder.append(SafeHtmlUtils.fromString(plainHelpText)))
                    .toSafeHtml();
        } else if (helpHTML != null) {
//            haveHelpText = true;
            effectiveHelpText = SafeHtmlUtils.fromTrustedString(helpHTML.getHTML());
        } else {
//            haveHelpText = false;
            effectiveHelpText = null;
        }

        helpButton.setHelpContent(effectiveHelpText);

//        if (haveHelpText) {
//            helpButton.setHelpContent(effectiveHelpText);
//        } else {
//            helpButton.setHelpContent(null);
//        }
        updateLabelPanel();

//        if (NullSafe.isNonBlankString(plainHelpText)) {
//            haveHelpText = true;
//            // Escape any html in there
//            effectiveHelpText = SafeHtmlUtils.fromString(plainHelpText);
//        }
//
//        if (NullSafe.isBlankString(helpText) && helpHTML != null) {
//            effectiveHelpText = SafeHtmlUtils.fromTrustedString(helpHTML.getHTML());
//            haveHelpText = true;
//        } else {
//
//        }
//        if (!NullSafe.isBlankString(getHelpText())) {
//            helpButton.setHelpContent(SafeHtmlUtils.fromSafeConstant(getHelpText()));
//        } else {
//            helpButton.setHelpContent(null);
//        }
//        updateLabelPanel();
    }

    @Override
    public void add(final Widget widget) {
//        GWT.log("Adding widget " + widget.getClass().getName());

        if (widget instanceof HelpHTML) {
            addHelpHtml((HelpHTML) widget);
        } else if (widget instanceof DescriptionHTML) {
            addDescriptionHtml((DescriptionHTML) widget);
        } else {
            // Not a HelpHTML so must be the childWidget
            if (childWidget != null) {
                throw new IllegalStateException("FormGroup can only contain one child widget that is not a HelpHTML. " +
                                                "Class: " + widget.getClass().getName());
            }
            this.childWidget = widget;
            if (id != null) {
                widget.getElement().setId(id);
            }
            widget.addStyleName("allow-focus");
        }
        updateFormGroupPanel();
//
//        formGroupPanel.clear();
//        formGroupPanel.add(labelPanel);
//        formGroupPanel.add(descriptionPanel);
//        formGroupPanel.add(widget);
//        formGroupPanel.add(feedback);
    }


    private void addDescriptionHtml(final DescriptionHTML descriptionHTML) {
        if (this.descriptionHTML != null) {
            throw new IllegalStateException("FormGroup can only contain one child DescriptionHTML widget. " +
                                            "Class: " + descriptionHTML.getClass().getName());
        }
        this.descriptionHTML = descriptionHTML;
        this.descriptionHTML.setStyleName("form-group-description");
        updateDescriptionPanel();
    }

    private void addHelpHtml(final HelpHTML helpHTML) {
        if (this.helpHTML != null) {
            throw new IllegalStateException("FormGroup can only contain one child HelpHTML widget. " +
                                            "Class: " + helpHTML.getClass().getName());
        }
        this.helpHTML = helpHTML;
        this.helpHTML.setStyleName(CLASS_NAME_FORM_GROUP_HELP);

        updateHelpButton();
//        // helpText trumps helpHTML
//        if (NullSafe.isBlankString(helpText)) {
//            this.helpText = this.helpHTML.getHTML();
//        }
    }

    @Override
    public void clear() {
        childWidget = null;
        helpHTML = null;
        helpText = null;
        descriptionHTML = null;
        updateLabelPanel();
        updateDescriptionPanel();
        updateFormGroupPanel();
//        formGroupPanel.clear();
//        formGroupPanel.add(labelPanel);
//        formGroupPanel.add(descriptionPanel);
//        formGroupPanel.add(feedback);
    }

    private void updateFormGroupPanel() {
        formGroupPanel.clear();
        formGroupPanel.add(labelPanel);
        if (descriptionHTML != null) {
            formGroupPanel.add(descriptionPanel);
        }
        if (childWidget != null) {
            formGroupPanel.add(childWidget);
        }
        formGroupPanel.add(feedbackLabel);

    }

    private void updateLabelPanel() {
        labelPanel.clear();
        if (NullSafe.isNonBlankString(formLabel.getLabel())) {
            labelPanel.add(formLabel);
        }

        if (helpButton.hasHelpContent()) {
            labelPanel.add(helpButton);
        }
    }

    private void updateDescriptionPanel() {
        descriptionPanel.clear();
        NullSafe.consume(descriptionHTML, descriptionPanel::add);
    }

    @Override
    public Iterator<Widget> iterator() {
        return Collections.singleton(childWidget).iterator();
    }

    @Override
    public boolean remove(final Widget w) {
        if (childWidget == w) {
            clear();
            return true;
        } else {
            return false;
        }
    }
}
