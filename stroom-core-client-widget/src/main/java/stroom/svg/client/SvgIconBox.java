package stroom.svg.client;

import stroom.svg.shared.SvgImage;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class SvgIconBox extends FlowPanel {

    public SvgIconBox() {
        setStyleName("svgIconBox");
    }

    public void setWidget(final Widget widget, final SvgImage svgImage) {
        this.add(widget);

        final SimplePanel icon = new SimplePanel();
        icon.getElement().setInnerHTML(svgImage.getSvg());
        icon.getElement().setClassName("svgIconBox-icon icon-colour__grey svgIcon " + svgImage.getClassName());

        this.add(icon);
    }
}
