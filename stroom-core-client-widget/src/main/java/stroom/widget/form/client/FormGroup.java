package stroom.widget.form.client;

import stroom.util.shared.GwtNullSafe;
import stroom.widget.help.client.HelpButton;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
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
 * For plain text help (or if you want to escape html special chars) use
 * the {@code helpText} attr:
 * <pre>{@code
 * <form:FormGroup ... helpText="This is my help text">
 * }</pre>
 * <p>
 * For rich help text then add a {@link HelpHTML} element containing the HTML help content:
 * <pre>{@code
 * <form:FormGroup ...>
 *     <form:HelpHTML>
 *         <p>This is para 1</p>
 *         <p>This is some <code>code</code></p>
 *     </form:HelpHTML>
 *     <g:TextBox ui:field="textBox" addStyleNames="w-100"/>
 * </form:FormGroup>
 * }</pre>
 * <p>
 * {@code formLabel} will be automatically added as a {@code <h4>} heading
 * at the top of the help popup.
 * </p>
 * <p>
 * To add descriptive text below the label (that is always visible, unlike the help),
 * use {@link DescriptionHTML} like this:
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

    private final FlowPanel formGroupPanel = new FlowPanel();
    private final FormLabel formLabel = new FormLabel();
    private final HelpButton helpButton = HelpButton.create();
    private final FlowPanel labelPanel = new FlowPanel();
    private final FlowPanel descriptionPanel = new FlowPanel();
    private final Label feedbackLabel = new Label();

    private String id;
    private Widget childWidget = null;
    private HelpHTML helpHTML = null;
    private String helpText = null;
    private DescriptionHTML descriptionHTML = null;

    public FormGroup() {
        feedbackLabel.setStyleName("invalid-feedback");
        formGroupPanel.addStyleName("form-group");
        labelPanel.addStyleName("form-group-label-container");
        formLabel.addStyleName("form-group-label");
        descriptionPanel.addStyleName("form-group-description-container");

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

            if (GwtNullSafe.isBlankString(label)) {
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
     * The helpText attr trumps the {@code <label class="helpText">}.
     */
    @SuppressWarnings("unused")
    public void setHelpText(final String helpText) {
        this.helpText = helpText;
        updateHelpButton();
    }

    private String getHelpText() {
        return helpText;
    }

    private void updateHelpButton() {
        if (!GwtNullSafe.isBlankString(getHelpText())) {
            helpButton.setHelpContent(SafeHtmlUtils.fromSafeConstant(getHelpText()));
        } else {
            helpButton.setHelpContent(null);
        }
        updateLabelPanel();
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
        this.helpText = this.helpHTML.getHTML();
        this.helpHTML.setStyleName("form-group-help");
        updateHelpButton();
        updateLabelPanel();
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
        labelPanel.add(formLabel);

        if (helpButton.hasHelpContent()) {
            labelPanel.add(helpButton);
        }
    }

    private void updateDescriptionPanel() {
        descriptionPanel.clear();
        GwtNullSafe.consume(descriptionHTML, descriptionPanel::add);
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
