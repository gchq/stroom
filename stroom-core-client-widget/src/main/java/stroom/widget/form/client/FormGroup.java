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
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
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

    public static final String STYLE_FORM_GROUP_DESCRIPTION_ROW = "form-group__description-row";
    public static final String STYLE_FORM_GROUP_DESCRIPTION_ROW_DISABLED =
            STYLE_FORM_GROUP_DESCRIPTION_ROW + "--disabled";
    // A group with no label of its own whose tick box has one: the help goes beside that label
    public static final String STYLE_FORM_GROUP_INLINE_TICK = "form-group--inline-tick";

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
    // A list of options to choose from that isn't a select element, e.g. a selection list
    private static final String LIST_BOX_ROLE = "listbox";
    private static final String LIST_BOX_SELECTOR = "[role=\"listbox\"]";
    private static final Set<String> NON_LABELLED_INPUT_TYPES = Set.of(
            "hidden", "button", "submit", "reset", "image");

    private static final Binder BINDER = GWT.create(Binder.class);

    // The parts, declared in FormGroup.ui.xml
    @UiField
    FlowPanel formGroupPanel;
    @UiField
    FormLabel formLabel;
    // Made here, as it is private to the package
    @UiField(provided = true)
    final FormGroupHelpButton helpButton = new FormGroupHelpButton();
    @UiField
    FlowPanel labelPanel;
    @UiField
    FlowPanel descriptionPanel;
    @UiField
    Label feedbackLabel;
    // The short screen reader description (or else the plain help text), hidden, as the description
    // of the control the label is for, so that a screen reader reads it when the control is focused
    @UiField
    Label helpDescription;
    // The short description a screen reader reads, or null to use the plain help text
    private String screenReaderText;
    // Whether the control must have a value, so assistive technology says it is required
    private boolean required;
    // The control marked as required, or null for none
    private Element requiredControl;
    // What is wrong with the control's value, or null if nothing is
    private String feedback;
    // The feedback last set by the control's own check of its input, or null (setInvalidReason)
    private String invalidReason;
    // The full form of the feedback, the tooltip and what screen readers read, or null if the shown
    // feedback says it all
    private String fullFeedback;
    // The control (or group) marked invalid and described by the feedback, or null for none
    private Element invalidControl;
    // The names of the controls in a group of several, in the order they are shown
    private List<String> controlNames = List.of();
    // The form control the label is for, or null if there is none (or several)
    private Element labelledControl;
    // A control that isn't a form element (e.g. a document picker), named by the label with
    // aria-labelledby, or null for none
    private Element ariaLabelledControl;
    // The child, when it holds several form controls, marked as a group labelled by the label
    private Element groupElement;
    // Whether the group's role is ours to remove (a radio group's is its own)
    private boolean groupRoleAdded;
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
        initWidget(BINDER.createAndBindUi(this));
        // Only the parts in use are shown, arranged once there is something to show (add)
        formGroupPanel.clear();
        helpDescription.getElement().setId(DOM.createUniqueId());
        // So that a group of several controls can be labelled by it
        formLabel.getElement().setId(DOM.createUniqueId());
        updateLabelPanel();

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
            if (isListBox(target)) {
                // A label element can't be for it, so it is named by the label alone, as its own
                // text is every option's
                formLabel.setIdentity(id);
                ariaLabelledControl = target;
                ariaLabelledControl.setAttribute(ARIA_LABELLED_BY, formLabel.getElement().getId());
            } else if (!isLabelable(target)) {
                // A label element can't be for it, so it is named by the label then its own text
                // (e.g. a picker's chosen document), e.g. 'Pipeline, My Pipeline, button'
                formLabel.setIdentity(id);
                ariaLabelledControl = target;
                ariaLabelledControl.setAttribute(ARIA_LABELLED_BY,
                        formLabel.getElement().getId() + " " + ensureId(target));
            } else if (target == childWidget.getElement()) {
                // The child is the control: the label is for its id, given one if the group has no
                // identity (it once had no 'for' then, so the control had no name)
                formLabel.setIdentity(NullSafe.isNonBlankString(id)
                        ? id
                        : ensureId(target));
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
                // A group of a kind of its own (a radio group) keeps its role
                groupRoleAdded = !groupElement.hasAttribute(ROLE);
                if (groupRoleAdded) {
                    groupElement.setAttribute(ROLE, GROUP_ROLE);
                }
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
        updateFeedback();
    }

    private void updateRequired() {
        // Shown on screen too, by an asterisk after the label
        formLabel.setRequired(required);
        if (requiredControl != null) {
            requiredControl.removeAttribute(ARIA_REQUIRED);
            requiredControl = null;
        }
        // Only a form element can be required (a picker's button can't, as ARIA doesn't allow it)
        if (required && labelledControl != null && isLabelable(labelledControl)) {
            requiredControl = labelledControl;
            requiredControl.setAttribute(ARIA_REQUIRED, "true");
        }
    }

    // Shows the feedback under the control, and marks the control (or group) invalid and described
    // by the feedback, so that a screen reader reads what is wrong when the control is focused
    private void updateFeedback() {
        if (invalidControl != null) {
            FieldValidity.setInvalid(invalidControl, false);
            FieldValidity.removeDescribedBy(invalidControl, feedbackLabel.getElement().getId());
            invalidControl = null;
        }
        showFeedbackText();
        final Element target = labelledControl != null
                ? labelledControl
                : groupElement;
        if (feedback != null && target != null) {
            invalidControl = target;
            FieldValidity.setInvalid(invalidControl, true);
            // The message is read first, before any help the control also has
            FieldValidity.addDescribedBy(invalidControl, ensureId(feedbackLabel.getElement()), true);
        }
    }

    // The feedback as shown: its short form, and, when there is a fuller one, that as its tooltip and
    // (in place of the short form, which is hidden from them) for screen readers
    private void showFeedbackText() {
        // In the group only while there is feedback to show
        if (feedback == null) {
            feedbackLabel.removeFromParent();
        } else if (feedbackLabel.getParent() != formGroupPanel) {
            // Before the hidden help text, which is last (once the group has been laid out)
            final int helpIndex = formGroupPanel.getWidgetIndex(helpDescription);
            if (helpIndex >= 0) {
                formGroupPanel.insert(feedbackLabel, helpIndex);
            } else {
                formGroupPanel.add(feedbackLabel);
            }
        }
        final Element element = feedbackLabel.getElement();
        if (feedback != null && fullFeedback != null) {
            element.setInnerText("");
            final Element shown = DOM.createSpan();
            shown.setInnerText(feedback);
            shown.setAttribute("aria-hidden", "true");
            final Element full = DOM.createSpan();
            full.setClassName("sr-only");
            full.setInnerText(fullFeedback);
            element.appendChild(shown);
            element.appendChild(full);
            element.setTitle(fullFeedback);
        } else {
            feedbackLabel.setText(NullSafe.string(feedback));
            element.removeAttribute("title");
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
            if (groupRoleAdded) {
                groupElement.removeAttribute(ROLE);
            }
            groupElement.removeAttribute(ARIA_LABELLED_BY);
            groupElement = null;
        }
    }

    // Tells assistive technology about the control the label is for (or the group of controls): its
    // short description is read when it is focused, and the help shortcut (F1 unless the user has
    // changed it) shows its help (as the help button isn't in the tab order). The description is the
    // screen reader text, or else the plain help text; rich help (HTML) may be long, so without
    // screen reader text it is only shown by the help shortcut or the help button.
    private void updateHelpDescription() {
        final String helpDescriptionId = helpDescription.getElement().getId();
        if (describedControl != null) {
            FieldValidity.removeDescribedBy(describedControl, helpDescriptionId);
            describedControl.removeAttribute(ARIA_KEY_SHORTCUTS);
            describedControl = null;
        }
        final String description = isRequiredButton()
                ? joinSentences("Required.", getDescription())
                : getDescription();
        helpDescription.setText(description);
        final Element target = labelledControl != null
                ? labelledControl
                : groupElement;
        if (target != null && (helpButton.hasHelpContent() || !description.isEmpty())) {
            describedControl = target;
            final String shortcut = KeyBinding.getAriaShortcut(Action.HELP);
            if (helpButton.hasHelpContent() && shortcut != null) {
                describedControl.setAttribute(ARIA_KEY_SHORTCUTS, shortcut);
            }
            if (!description.isEmpty()) {
                // After any validation feedback, which is read first
                FieldValidity.addDescribedBy(describedControl, helpDescriptionId, false);
            }
        }
    }

    // Whether the control is required but can't say so itself (a picker's button can't be
    // aria-required), so its description says it is
    private boolean isRequiredButton() {
        return required && labelledControl != null && !isLabelable(labelledControl);
    }

    private static String joinSentences(final String first, final String second) {
        return second.isEmpty()
                ? first
                : first + " " + second;
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
                String.join(", ", FORM_CONTROL_TAGS) + ", " + PICKER_SELECTOR + ", " + LIST_BOX_SELECTOR);
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
            // Hidden inline, or with the hidden attribute (e.g. a file chooser's file input)
            if ("none".equals(current.getStyle().getDisplay()) || current.hasAttribute("hidden")) {
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
    // (but not a hidden input, or a button, which is named by its own text), a control that opens
    // a dialog to choose a value (e.g. a document picker), or a list box (e.g. a selection list)
    private static boolean isFormControl(final Element element) {
        if (isPicker(element) || isListBox(element)) {
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

    // A control that opens a dialog to choose a value, e.g. a document picker (DropDownBox)
    private static boolean isPicker(final Element element) {
        return BUTTON_ROLE.equals(element.getAttribute(ROLE))
               && !NullSafe.isBlankString(element.getAttribute(ARIA_HAS_POPUP));
    }

    // A list of options that isn't a select element, e.g. a selection list's (SelectionList)
    private static boolean isListBox(final Element element) {
        return LIST_BOX_ROLE.equals(element.getAttribute(ROLE));
    }

    public void setLabel(final String label) {
        if (!Objects.equals(getLabel(), label)) {
            formLabel.setLabel(label);
            updateHelpTitle();
            updateLabelPanel();
            updateInlineTick();
        }
    }

    // The help is for the group's label or, if it has none, the label beside its tick box
    private void updateHelpTitle() {
        final String label = NullSafe.isNonBlankString(getLabel())
                ? getLabel()
                : getTickBoxLabel();
        // The shortcut, as menu items show theirs, so that it can be discovered
        final String shortcut = KeyBinding.getShortcut(Action.HELP);
        final String clickForHelp = shortcut == null
                ? "Click for help"
                : "Click for help (" + shortcut + ")";
        if (NullSafe.isBlankString(label)) {
            helpButton.setTitle(clickForHelp);
        } else {
            helpButton.setTitle(label + " - " + clickForHelp);
            helpButton.setHelpContentHeading(label);
        }
    }

    private String getTickBoxLabel() {
        return childWidget instanceof final TickBox tickBox
                ? tickBox.getLabel()
                : null;
    }

    // A tick box with its own label and no group label has its help on the same line, after that
    // label, rather than on a line of its own above it
    private void updateInlineTick() {
        final boolean inline = NullSafe.isBlankString(getLabel())
                               && NullSafe.isNonBlankString(getTickBoxLabel());
        formGroupPanel.setStyleName(STYLE_FORM_GROUP_INLINE_TICK, inline);
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
    /// technology says it is required (`aria-required`), and its label ends with an asterisk. It
    /// doesn't validate it. Bound to the `required` attribute in ui.xml.
    ///
    /// @param required Whether the control must have a value.
    public void setRequired(final boolean required) {
        this.required = required;
        updateRequired();
        updateHelpDescription();
    }

    /// Shows what is wrong with the control's value, under the control. The control is marked invalid
    /// (`aria-invalid`) and described by the message (`aria-describedby`), so that a screen reader
    /// reads the message when the control is focused. Focus the control after calling this so the
    /// user goes straight to what needs fixing.
    ///
    /// @param feedback What is wrong with the value, e.g. `You must provide a name`, or null or blank
    ///                 to clear it (see [#clearFeedback()]).
    public void setFeedback(final String feedback) {
        setFeedback(feedback, null);
    }

    /// Shows what is wrong with the control's value, as [#setFeedback(String)] does, in a short form
    /// under the control, with a full form (e.g. with what to do about it) as its tooltip and as what
    /// a screen reader reads.
    ///
    /// @param feedback     What is wrong with the value, briefly, or null or blank to clear it.
    /// @param fullFeedback The full message, or null or blank if the short one says it all.
    public void setFeedback(final String feedback, final String fullFeedback) {
        this.feedback = NullSafe.isBlankString(feedback)
                ? null
                : feedback;
        this.fullFeedback = this.feedback == null || NullSafe.isBlankString(fullFeedback)
                            || fullFeedback.equals(feedback)
                ? null
                : fullFeedback;
        updateFeedback();
    }

    /// Clears any feedback, marking the control valid again.
    public void clearFeedback() {
        setFeedback(null);
    }

    /// Says why the control's input can't be kept, as [#setFeedback(String)] does, or clears that
    /// once it can, leaving feedback set otherwise (e.g. by a controller) alone. A field that checks
    /// its own input calls it (see [InvalidInputs#showReason(HasInvalidInput)]).
    ///
    /// @param reason Why the input can't be kept, or null if it can.
    public void setInvalidReason(final String reason) {
        if (reason != null) {
            invalidReason = reason;
            setFeedback(reason);
        } else if (invalidReason != null) {
            if (invalidReason.equals(feedback)) {
                clearFeedback();
            }
            invalidReason = null;
        }
    }

    /// @return What is wrong with the control's value, or null if nothing is.
    public String getFeedback() {
        return feedback;
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
            descriptionPanel.addStyleName(STYLE_FORM_GROUP_DESCRIPTION_ROW_DISABLED);
        } else {
            descriptionPanel.removeStyleName(STYLE_FORM_GROUP_DESCRIPTION_ROW_DISABLED);
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
        this.descriptionHTML.setStyleName("form-group__description");
        updateDescriptionPanel();
    }

    private void addHelpHtml(final HelpHTML helpHTML) {
        if (this.helpHTML != null) {
            throw new IllegalStateException("FormGroup can only contain one child HelpHTML widget. " +
                                            "Class: " + helpHTML.getClass().getName());
        }
        this.helpHTML = helpHTML;

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
        if (feedback != null) {
            formGroupPanel.add(feedbackLabel);
        }
        formGroupPanel.add(helpDescription);
        updateHelpTitle();
        updateInlineTick();
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<FlowPanel, FormGroup> {

    }
}
