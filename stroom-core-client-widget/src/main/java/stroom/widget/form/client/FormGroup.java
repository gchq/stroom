package stroom.widget.form.client;

import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.HelpButton;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
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
 * <form:FormGroup
 *     ...
 *     helpText="This is my help text"
 * }</pre>
 * <p>
 * For rich help text then add a {@code label} element (wrapped in a
 * {@link HTMLPanel}) like this:
 * <pre>{@code
 * <form:FormGroup ...>
 *     <g:HTMLPanel addStyleNames="dock-min">
 *         <label class="helpText">
 *             <p>This is para 1</p>
 *             <p>This is some <code>code</code></p>
 *         </label>
 *         <g:TextBox ui:field="textBox" addStyleNames="w-100"/>
 *     </g:HTMLPanel>
 * </form:FormGroup>
 * }</pre>
 */
public class FormGroup extends Composite implements HasWidgets {

    private final FlowPanel formGroup = new FlowPanel();
    private final FormLabel formLabel = new FormLabel();
    private final HelpButton helpButton = HelpButton.create();
    private final FlowPanel labelPanel = new FlowPanel();
    private final Label feedback = new Label();

    private String id;
    private Widget widget;
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
        if (widget != null) {
            widget.getElement().setId(id);
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
        if (widget != null) {
            throw new IllegalStateException("FormRow can only contain one child widget");
        }

        // The helpText attr trumps a child label elm so no point looking for one
        if (w instanceof HTMLPanel) {
            //noinspection PatternVariableCanBeUsed // Cos GWT
            final HTMLPanel htmlPanel = (HTMLPanel) w;

            final Element panelElm = htmlPanel.getElement();

            if (panelElm.hasChildNodes()) {
                Node helpLabelElm = null;
                for (int i = 0; i < panelElm.getChildCount(); i++) {
                    final Node child = panelElm.getChild(i);
                    if (child instanceof LabelElement) {
                        //noinspection PatternVariableCanBeUsed  // cos GWT
                        final LabelElement labelElm = (LabelElement) child;
                        final boolean isHelpLabel = GwtNullSafe.test(
                                labelElm,
                                LabelElement::getClassName,
                                classAttr -> classAttr.contains("helpText"));
                        if (isHelpLabel) {
                            helpText = labelElm.getInnerHTML();
                            updateHelpButton();
                            if (helpLabelElm != null) {
                                throw new IllegalStateException("Found two label elements with class 'helpText'");
                            }
                            helpLabelElm = child;
                        }
                    }
                }
                // Prevent the label element containing the help text from being seen in the browser
                // now we have extracted its content
                if (helpLabelElm != null) {
                    panelElm.removeChild(helpLabelElm);
                }
            }
        }

        this.widget = w;
        if (id != null) {
            w.getElement().setId(id);
        }

        w.addStyleName("allow-focus");
        formGroup.clear();

        updateLabelPanel();
        formGroup.add(labelPanel);
        formGroup.add(w);
        formGroup.add(feedback);
    }

    @Override
    public void clear() {
        widget = null;

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
        return Collections.singleton(widget).iterator();
    }

    @Override
    public boolean remove(final Widget w) {
        if (widget == w) {
            clear();
            return true;
        } else {
            return false;
        }
    }
}
