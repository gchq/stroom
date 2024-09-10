package stroom.svg.client;

import stroom.svg.shared.SvgImage;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class SvgIconBox extends FlowPanel {

    public static final String ICON_BOX_READONLY_CLASS_NAME = "svgIconBox-readonly";
    private SimplePanel outer;
    private SimplePanel inner;
    private boolean isReadonly = false;

    public SvgIconBox() {
        setStyleName("svgIconBox");
    }

    public void setWidget(final Widget widget, final SvgImage svgImage) {
        this.add(widget);

        inner = new SimplePanel();
        inner.getElement().setInnerHTML(svgImage.getSvg());
        inner.getElement().setClassName("svgIconBox-icon-inner icon-colour__grey svgIcon " + svgImage.getClassName());

        outer = new SimplePanel(inner);
        outer.getElement().setClassName("svgIconBox-icon-outer");

        this.add(outer);
    }

    public HandlerRegistration addClickHandler(ClickHandler handler) {
        return outer.addDomHandler(event -> {
            if (!isReadonly()) {
                handler.onClick(event);
            }
        }, ClickEvent.getType());
    }

    public void setReadonly(boolean isReadonly) {
        this.isReadonly = isReadonly;
        if (isReadonly) {
            inner.getElement().addClassName(ICON_BOX_READONLY_CLASS_NAME);
        } else {
            inner.getElement().removeClassName(ICON_BOX_READONLY_CLASS_NAME);
        }
    }

    private boolean isReadonly() {
        return isReadonly;
    }
}
