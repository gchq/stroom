package stroom.widget.form.client;

import stroom.svg.client.SvgPresets;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tooltip.client.event.ShowHelpEvent;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Iterator;

public class FormGroup extends Composite implements HasWidgets {

    private final FlowPanel formGroup = new FlowPanel();
    private final FormLabel formLabel = new FormLabel();
    private final SvgButton helpButton = SvgButton.create(SvgPresets.HELP);
    private final FlowPanel labelPanel = new FlowPanel();
    private final Label feedback = new Label();

    private String id;
    private Widget widget;
    private String description = null;
    private String helpText = null;
    private boolean isHelpShowing = false;
    private String labelText = null;

    public FormGroup() {

        feedback.setStyleName("invalid-feedback");
        formGroup.addStyleName("form-group");
        labelPanel.addStyleName("form-group-label-container");
        formLabel.addStyleName("form-group-label");

        helpButton.addStyleName("form-group-help info");
        helpButton.addClickHandler(event -> showHelpPopup(event.getRelativeElement()));
        helpButton.addKeyDownHandler(event -> {
            if (KeyCodes.KEY_SPACE == event.getNativeKeyCode()) {
                showHelpPopup(event.getRelativeElement());
            }
        });
        helpButton.setVisible(false);

        labelPanel.add(formLabel);
        labelPanel.add(helpButton);

        initWidget(formGroup);
    }

    private boolean isHelpShowing() {
        return isHelpShowing;
    }

    private void showHelpPopup(final Element element) {
        if (!isHelpShowing()) {
            final SafeHtmlBuilder builder = new SafeHtmlBuilder();
            if (!GwtNullSafe.isBlankString(labelText)) {
                builder.appendHtmlConstant("<h4>")
                        .appendEscaped(labelText)
                        .appendHtmlConstant("</h4>");
            }
            final SafeHtml popupText = builder
                    .appendHtmlConstant(helpText)
                    .toSafeHtml();

            ShowHelpEvent.builder(element)
                    .withContent(popupText)
                    .fire();
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
        this.labelText = label;
        formLabel.setLabel(label);
        if (GwtNullSafe.isBlankString(label)) {
            helpButton.setTitle("Help");
        } else {
            helpButton.setTitle(label + " - Help");
        }
    }

    public void setDescription(final String description) {
//        this.description = description;
//        if (GwtNullSafe.isBlankString(description)) {
//            this.description = null;
//            helpButton.setVisible(false);
//        } else {
//            this.description = description.trim();
//            helpButton.setVisible(true);
//        }
    }

    public void setHelpText(final String helpText) {
        this.helpText = helpText;
        if (GwtNullSafe.isBlankString(helpText)) {
            this.helpText = null;
            helpButton.setVisible(false);
        } else {
            this.helpText = helpText.trim();
            helpButton.setVisible(true);
        }
    }

    @Override
    public void add(final Widget w) {
        if (widget != null) {
            throw new IllegalStateException("FormRow can only contain one child widget");
        }

        setHelpText(null);
//        GWT.log("widget: " + w.getClass().getName() + ", " + w.getElement().getInnerText());
        if (w instanceof HTMLPanel) {
            //noinspection PatternVariableCanBeUsed // Cos GWT
            final HTMLPanel htmlPanel = (HTMLPanel) w;

            final Node firstChild = htmlPanel.getElement().getFirstChild();
            if (firstChild instanceof LabelElement) {
                //noinspection PatternVariableCanBeUsed  // cos GWT
                final LabelElement labelElm = (LabelElement) firstChild;
                final String innerText = labelElm.getInnerHTML();
                labelElm.getStyle().setDisplay(Display.NONE);
//                htmlPanel.getElement().setInnerText("");
//                GWT.log(innerText);
                setHelpText(innerText);
            }
        }

        this.widget = w;
        if (id != null) {
            w.getElement().setId(id);
        }

        w.addStyleName("allow-focus");
        formGroup.clear();

//        updateLabelPanel();
        formGroup.add(labelPanel);
        formGroup.add(w);
        formGroup.add(feedback);
    }

    @Override
    public void clear() {
        widget = null;

//        updateLabelPanel();
        formGroup.clear();
        formGroup.add(labelPanel);
        formGroup.add(feedback);
    }

//    private void updateLabelPanel() {
//        labelPanel.clear();
//        labelPanel.add(formLabel);
//        labelPanel.add(helpButton);
//    }

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

    private static class HelpPopup extends PopupPanel {

        public HelpPopup(final SafeHtml content) {
//        public HelpPopup(final Supplier<String> popupTextSupplier) {
            // PopupPanel's constructor takes 'auto-hide' as its boolean parameter.
            // If this is set, the panel closes itself automatically when the user
            // clicks outside of it.
            super(true);

//            setWidget(new Label(popupTextSupplier.get(), true));
            setWidget(new HTMLPanel(content));
//            setWidget(new HTMLPanel(SafeHtmlUtils.fromTrustedString("<b>hello</b>")));
        }
    }
}
