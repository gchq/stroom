package stroom.widget.form.client;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

public class FormLabel extends Composite {

    private final Lbl lbl = new Lbl();

    public FormLabel() {
        lbl.setStyleName("form-label");
        initWidget(lbl);
    }

    public void setIdentity(final String id) {
        lbl.getElement().setAttribute("for", id);
    }

    public void setLabel(final String label) {
        lbl.setTitle(label);
        lbl.getElement().setInnerText(label);
    }

    private static class Lbl extends Widget {

        public Lbl() {
            setElement((com.google.gwt.dom.client.Element) DOM.createLabel());
        }
    }
}
