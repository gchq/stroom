package stroom.widget.form.client;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

public class FormLabel extends Composite {

    public static final String BASE_STYLE = "form-label";
    public static final String STYLE_FORM_LABEL_DISABLED = BASE_STYLE + "--disabled";

    private final Lbl lbl = new Lbl();
    private boolean disabled = false;

    public FormLabel() {
        lbl.setStyleName(BASE_STYLE);
        initWidget(lbl);
    }

    public void setIdentity(final String id) {
        lbl.getElement().setAttribute("for", id);
    }

    public void setLabel(final String label) {
        lbl.setTitle(label);
        lbl.getElement().setInnerText(label);
    }

    public String getLabel() {
        return lbl.getElement().getInnerText();
    }

    public void setDisabled(final boolean disabled) {
        this.disabled = disabled;
        if (disabled) {
            lbl.addStyleName(STYLE_FORM_LABEL_DISABLED);
        } else {
            lbl.removeStyleName(STYLE_FORM_LABEL_DISABLED);
        }
    }

    public boolean isDisabled() {
        return disabled;
    }

    // --------------------------------------------------------------------------------


    private static class Lbl extends Widget {

        public Lbl() {
            setElement((com.google.gwt.dom.client.Element) DOM.createLabel());
        }
    }
}
