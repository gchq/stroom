package stroom.svg.client;

import stroom.svg.shared.SvgImage;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class SvgIconBox extends FlowPanel {

    private SimplePanel icon;

    public SvgIconBox() {
        setStyleName("svgIconBox");
    }

    public void setWidget(final Widget widget, final SvgImage svgImage) {
        this.add(widget);

        icon = new SimplePanel();
        icon.getElement().setInnerHTML(svgImage.getSvg());
        icon.getElement().setClassName("svgIconBox-icon icon-colour__grey svgIcon " + svgImage.getClassName());

        this.add(icon);
    }

    public HandlerRegistration addClickHandler(ClickHandler handler) {
        return icon.addDomHandler(handler, ClickEvent.getType());
    }
}
