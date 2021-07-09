package stroom.widget.form.client;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Iterator;

public class FormRow extends Composite implements HasWidgets {

    private final SimplePanel formRow = new SimplePanel();
    private final FlowPanel formGroup = new FlowPanel();
    private final Lbl lbl = new Lbl();
    private final SimplePanel inputContainer = new SimplePanel();
    private final Label feedback = new Label();

    private String id;
    private Widget widget;

    public FormRow() {
        lbl.setStyleName("form-label");
        inputContainer.setStyleName("FormField__input-container");
        feedback.setStyleName("invalid-feedback");

        formGroup.setStyleName("form-group col");
        formGroup.add(lbl);
        formGroup.add(inputContainer);
        formGroup.add(feedback);

        formRow.setStyleName("form-row");
        formRow.setWidget(formGroup);

        initWidget(formRow);
    }

    public void setIdentity(final String id) {
        this.id = id;
        lbl.getElement().setAttribute("for", id);
        if (widget != null) {
            widget.getElement().setId(id);
        }
    }

    public void setLabel(final String label) {
        lbl.setTitle(label);
        lbl.getElement().setInnerText(label);
    }

//    @Override
//    public Widget getWidget() {
//        return inputContainer.getWidget();
//    }
//
//    @Override
//    public void setWidget(final IsWidget w) {
//        final Widget widget = w.asWidget();
//        widget.addStyleName("allow-focus");
//        inputContainer.setWidget(widget);
//    }

//
//    @Override
//    public void setWidget(final Widget widget) {
//        widget.addStyleName("allow-focus");
//        inputContainer.setWidget(widget);
////        super.setWidget(widget);
//    }
//
//    @Override
//    public void add(final Widget w) {
//        inputContainer.add(w);
//    }

    //    @Override
//    protected Element getContainerElement() {
//        return inputContainer.getElement();
//    }


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
        inputContainer.add(w);
    }

    @Override
    public void clear() {
        widget = null;
        inputContainer.clear();
    }

    @Override
    public Iterator<Widget> iterator() {
        return inputContainer.iterator();
    }

    @Override
    public boolean remove(final Widget w) {
        return inputContainer.remove(w);
    }

    public static class Lbl extends Widget {

        public Lbl() {
            setElement((com.google.gwt.dom.client.Element) DOM.createLabel());
        }
    }
}
