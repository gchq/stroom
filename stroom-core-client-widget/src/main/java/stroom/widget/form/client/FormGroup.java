package stroom.widget.form.client;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Iterator;

public class FormGroup extends Composite implements HasWidgets {

    private final FlowPanel formGroup = new FlowPanel();
    private final FormLabel formLabel = new FormLabel();
    private final Label feedback = new Label();

    private String id;
    private Widget widget;

    public FormGroup() {
        feedback.setStyleName("invalid-feedback");
        formGroup.addStyleName("form-group");
        initWidget(formGroup);
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
    }

    @Override
    public void add(final Widget w) {
        if (widget != null) {
            throw new IllegalStateException("FormRow can only contain one child widget");
        }

        this.widget = w;
        if (id != null) {
            w.getElement().setId(id);
        }

        w.addStyleName("allow-focus");
        formGroup.clear();

        formGroup.add(formLabel);
        formGroup.add(w);
        formGroup.add(feedback);
    }

    @Override
    public void clear() {
        widget = null;

        formGroup.clear();
        formGroup.add(formLabel);
        formGroup.add(feedback);
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
