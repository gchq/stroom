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

package stroom.widget.form.client;

import stroom.util.shared.NullSafe;
import stroom.widget.help.client.HelpButton;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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

    // The elements that are form controls a label can name
    private static final List<String> FORM_CONTROL_TAGS = List.of("input", "select", "textarea");
    // The inputs a label doesn't name: hidden ones, and buttons, which their own text names
    private static final String ARIA_KEY_SHORTCUTS = "aria-keyshortcuts";
    private static final String ARIA_LABEL = "aria-label";
    private static final String ARIA_REQUIRED = "aria-required";
    private static final String ARIA_LABELLED_BY = "aria-labelledby";
    private static final String ROLE = "role";
    private static final String GROUP_ROLE = "group";
    private static final String BUTTON_ROLE = "button";
    private static final String ARIA_HAS_POPUP = "aria-haspopup";
    // A control that opens a dialog to choose a value (see isPicker)
    private static final String PICKER_SELECTOR = "[role=\"button\"][aria-haspopup]";
    // The key that shows a group's help (see handleKeyEvent)
    private static final String HELP_SHORTCUT = "F1";
    private static final Set<String> NON_LABELLED_INPUT_TYPES = Set.of(
            "hidden", "button", "submit", "reset", "image");

    private final FlowPanel formGroupPanel = new FlowPanel();
    private final FormLabel formLabel = new FormLabel();
    private final HelpButton helpButton = HelpButton.create();
    private final FlowPanel labelPanel = new FlowPanel();
    private final FlowPanel descriptionPanel = new FlowPanel();
    private final Label feedbackLabel = new Label();
    // The short screen reader description (or else the plain help text), hidden, as the description
    // of the control the label is for, so that a screen reader reads it when the control is focused
    private final Label helpDescription = new Label();
    // The short description a screen reader reads, or null to use the plain help text
    private String screenReaderText;
    // Whether the control must have a value, so assistive technology says it is required
    private boolean required;
    // The control marked as required, or null for none
    private Element requiredControl;
    // The names of the controls in a group of several, in the order they are shown
    private List<String> controlNames = List.of();
    // The form control the label is for, or null if there is none (or several)
    private Element labelledControl;
    // A control that isn't a form element (e.g. a document picker), named by the label with
    // aria-labelledby, or null for none
    private Element ariaLabelledControl;
    // The child, when it holds several form controls, marked as a group labelled by the label
    private Element groupElement;
    // The control (or group) that has the description and F1 as its shortcut, or null for none
    private Element describedControl;

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
        helpDescription.getElement().setId(DOM.createUniqueId());
        // So that a group of several controls can be labelled by it
        formLabel.getElement().setId(DOM.createUniqueId());
        helpDescription.setVisible(false);
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
        if (childWidget != null) {
            childWidget.getElement().setId(id);
        }
        updateLabelTarget();
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        // The child may have changed its labelled element's id since it was added
        updateLabelTarget();
    }

    // Makes the label for the control the child takes input with, so that the label names it (and
    // clicking the label acts on it): the child itself if it is a form control, otherwise the one
    // form control inside it (e.g. a tick box's input, or a password box in a panel). Otherwise
    // (no form control, or several) the label is for the child, whose id is the group's identity.
    private void updateLabelTarget() {
        final List<Element> controls = childWidget == null
                ? List.of()
                : findFormControls(childWidget.getElement());
        clearGroup();
        clearAriaLabelledControl();
        labelledControl = null;
        if (controls.size() == 1) {
            final Element target = controls.get(0);
            if (!isLabelable(target)) {
                // A label element can't be for it, so it is named by the label then its own text
                // (e.g. a picker's chosen document), e.g. 'Pipeline, My Pipeline, button'
                formLabel.setIdentity(id);
                ariaLabelledControl = target;
                ariaLabelledControl.setAttribute(ARIA_LABELLED_BY,
                        formLabel.getElement().getId() + " " + ensureId(target));
            } else if (target == childWidget.getElement()) {
                formLabel.setIdentity(id);
            } else {
                formLabel.setIdentity(ensureId(target));
            }
            labelledControl = target;
        } else {
            formLabel.setIdentity(id);
            if (controls.size() > 1 && NullSafe.isNonBlankString(getLabel())) {
                // One label can't be for several controls, so the child is a group that the label
                // names, and each control is named by controlNames, e.g. 'Retain For, group, Unit'
                groupElement = childWidget.getElement();
                groupElement.setAttribute(ROLE, GROUP_ROLE);
                groupElement.setAttribute(ARIA_LABELLED_BY, formLabel.getElement().getId());
            }
        }
        for (int i = 0; i < controls.size() && i < controlNames.size(); i++) {
            if (!controlNames.get(i).isEmpty()) {
                controls.get(i).setAttribute(ARIA_LABEL, controlNames.get(i));
            }
        }
        updateHelpDescription();
        updateRequired();
    }

    private void updateRequired() {
        if (requiredControl != null) {
            requiredControl.removeAttribute(ARIA_REQUIRED);
            requiredControl = null;
        }
        if (required && labelledControl != null) {
            requiredControl = labelledControl;
            requiredControl.setAttribute(ARIA_REQUIRED, "true");
        }
    }

    private static String ensureId(final Element element) {
        if (NullSafe.isBlankString(element.getId())) {
            element.setId(DOM.createUniqueId());
        }
        return element.getId();
    }

    private void clearAriaLabelledControl() {
        if (ariaLabelledControl != null) {
            ariaLabelledControl.removeAttribute(ARIA_LABELLED_BY);
            ariaLabelledControl = null;
        }
    }

    private void clearGroup() {
        if (groupElement != null) {
            groupElement.removeAttribute(ROLE);
            groupElement.removeAttribute(ARIA_LABELLED_BY);
            groupElement = null;
        }
    }

    // Tells assistive technology about the control the label is for (or the group of controls): its
    // short description is read when it is focused, and F1 shows its help (as the help button isn't
    // in the tab order). The description is the screen reader text, or else the plain help text;
    // rich help (HTML) may be long, so without screen reader text it is only shown by F1 or the
    // help button.
    private void updateHelpDescription() {
        final String helpDescriptionId = helpDescription.getElement().getId();
        if (describedControl != null) {
            FieldValidity.removeDescribedBy(describedControl, helpDescriptionId);
            describedControl.removeAttribute(ARIA_KEY_SHORTCUTS);
            describedControl = null;
        }
        final String description = getDescription();
        helpDescription.setText(description);
        final Element target = labelledControl != null
                ? labelledControl
                : groupElement;
        if (target != null && (helpButton.hasHelpContent() || !description.isEmpty())) {
            describedControl = target;
            if (helpButton.hasHelpContent()) {
                describedControl.setAttribute(ARIA_KEY_SHORTCUTS, HELP_SHORTCUT);
            }
            if (!description.isEmpty()) {
                // After any validation feedback, which is read first
                FieldValidity.addDescribedBy(describedControl, helpDescriptionId, false);
            }
        }
    }

    // The screen reader text, or else the plain help text, or else nothing
    private String getDescription() {
        if (NullSafe.isNonBlankString(screenReaderText)) {
            return screenReaderText.trim();
        }
        return helpTextOverride == null && NullSafe.isNonBlankString(helpText)
                ? helpText
                : "";
    }

    /// @param root The child's element.
    /// @return The form controls a label for the child could be for, in the order they are shown: the
    /// element itself if it is one, otherwise those inside it that aren't hidden.
    static List<Element> findFormControls(final Element root) {
        if (isFormControl(root)) {
            return List.of(root);
        }
        final List<Element> controls = new ArrayList<>();
        final NodeList<Element> elements = querySelectorAll(root,
                String.join(", ", FORM_CONTROL_TAGS) + ", " + PICKER_SELECTOR);
        for (int i = 0; i < elements.getLength(); i++) {
            final Element element = elements.getItem(i);
            if (isFormControl(element) && !isHidden(element, root)) {
                controls.add(element);
            }
        }
        return controls;
    }

    // Whether the element, or a parent of it inside the root, is hidden (e.g. a widget made
    // invisible, such as a grid pager's text boxes, which are only shown while editing)
    private static boolean isHidden(final Element element, final Element root) {
        Element current = element;
        while (current != null && current != root) {
            if ("none".equals(current.getStyle().getDisplay())) {
                return true;
            }
            current = current.getParentElement();
        }
        return false;
    }

    private static native NodeList<Element> querySelectorAll(Element root, String selectors) /*-{
        return root.querySelectorAll(selectors);
    }-*/;

    // Whether the element is a form control a label can name and that takes input: a form element
    // (but not a hidden input, or a button, which is named by its own text), or a control that opens
    // a dialog to choose a value (e.g. a document picker)
    private static boolean isFormControl(final Element element) {
        if (isPicker(element)) {
            return true;
        }
        final String tagName = element.getTagName().toLowerCase(Locale.ROOT);
        if ("input".equals(tagName)) {
            // GWT gives "" for a missing attribute
            final String type = element.getAttribute("type").toLowerCase(Locale.ROOT);
            return !NON_LABELLED_INPUT_TYPES.contains(type);
        }
        return FORM_CONTROL_TAGS.contains(tagName);
    }

    // Whether a label element can be for the element: only form elements can
    private static boolean isLabelable(final Element element) {
        return FORM_CONTROL_TAGS.contains(element.getTagName().toLowerCase(Locale.ROOT));
    }

    // A control that opens a dialog to choose a value, e.g. a document picker (DropDownViewImpl)
    private static boolean isPicker(final Element element) {
        return BUTTON_ROLE.equals(element.getAttribute(ROLE))
               && !NullSafe.isBlankString(element.getAttribute(ARIA_HAS_POPUP));
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

    /// Sets the short description a screen reader reads each time the control (or group of
    /// controls) is focused, e.g. its format or units. Without it, plain help text is read instead.
    /// Bound to the `screenReaderText` attribute in ui.xml.
    ///
    /// @param screenReaderText The description, or null or blank for none.
    public void setScreenReaderText(final String screenReaderText) {
        this.screenReaderText = screenReaderText;
        updateHelpDescription();
    }

    /// Marks the control the label is for as one that must have a value, so that assistive
    /// technology says it is required (`aria-required`). It doesn't change how the field looks or
    /// validate it. Bound to the `required` attribute in ui.xml.
    ///
    /// @param required Whether the control must have a value.
    public void setRequired(final boolean required) {
        this.required = required;
        updateRequired();
    }

    /// Names the controls of a group that has several (e.g. a number and its unit), which the label
    /// can't name, as they are shown. The group itself is named by the label. Bound to the
    /// `controlNames` attribute in ui.xml.
    ///
    /// @param controlNames The names, separated by commas, e.g. `Amount, Unit`.
    public void setControlNames(final String controlNames) {
        this.controlNames = ControlNames.parse(controlNames);
        updateLabelTarget();
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
        updateHelpDescription();

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
            updateLabelTarget();
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
        updateLabelTarget();
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
        formGroupPanel.add(helpDescription);

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
