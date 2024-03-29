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
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Iterator;

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
 * For rich help text then add a {@code label} element (wrapped in a
 * {@link HTMLPanel}) like this:
 * <pre>{@code
 * <form:FormGroup ...>
 *     <g:HTML>
 *         <p>This is para 1</p>
 *         <p>This is some <code>code</code></p>
 *     </g:HTML>
 *     <g:TextBox ui:field="textBox" addStyleNames="w-100"/>
 * </form:FormGroup>
 * }</pre>
 * <p>
 * {@code formLabel} will be automatically added as a {@code <h4>} heading
 * at the top of the help popup.
 * </p>
 */
public class FormGroup extends Composite implements HasWidgets {

    private final FlowPanel formGroup = new FlowPanel();
    private final FormLabel formLabel = new FormLabel();
    private final HelpButton helpButton = HelpButton.create();
    private final FlowPanel labelPanel = new FlowPanel();
    private final Label feedback = new Label();

    private String id;
    private Widget childWidget = null;
    private HTML helpHTML = null;
    private String helpText = null;

    public FormGroup() {
        feedback.setStyleName("invalid-feedback");
        formGroup.addStyleName("form-group");
        labelPanel.addStyleName("form-group-label-container");
        formLabel.addStyleName("form-group-label");

        // Don't want the user to have to tab over each help btn when you can
        // call up the help with F1
        helpButton.preventTabFocus();
        updateLabelPanel();

        initWidget(formGroup);

        formGroup.addDomHandler(
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
        formLabel.setLabel(label);

        if (GwtNullSafe.isBlankString(label)) {
            helpButton.setTitle("Help");
        } else {
            helpButton.setTitle(label + " - Help");
            helpButton.setHelpContentHeading(label);
        }
        updateLabelPanel();
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
    public void add(final Widget w) {
//        GWT.log("Adding widget " + w.getClass().getName());

        if (w instanceof HTML) {
            if (helpHTML != null) {
                throw new IllegalStateException("FormGroup can only contain one child HelpHTML widget. " +
                        "Class: " + w.getClass().getName());
            }
            this.helpHTML = (HTML) w;
            this.helpText = helpHTML.getHTML();
            updateHelpButton();
        } else {
            // Not a HelpHTML so must be the childWidget
            if (childWidget != null) {
                throw new IllegalStateException("FormGroup can only contain one child widget that is not a HelpHTML. " +
                        "Class: " + w.getClass().getName());
            }
            this.childWidget = w;
            if (id != null) {
                w.getElement().setId(id);
            }
            w.addStyleName("allow-focus");
        }

        formGroup.clear();
        updateLabelPanel();
        formGroup.add(labelPanel);
        formGroup.add(w);
        formGroup.add(feedback);
    }

    @Override
    public void clear() {
        childWidget = null;
        updateLabelPanel();
        formGroup.clear();
        formGroup.add(labelPanel);
        formGroup.add(feedback);
    }

    private void updateLabelPanel() {
        labelPanel.clear();
        labelPanel.add(formLabel);

        if (helpButton.hasHelpContent()) {
            labelPanel.add(helpButton);
        }
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
