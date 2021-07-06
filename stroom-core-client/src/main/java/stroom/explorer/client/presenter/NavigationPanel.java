package stroom.explorer.client.presenter;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class NavigationPanel extends Composite {

    private final SimplePanel arrow = new SimplePanel();
    private final FlowPanel header = new FlowPanel();
    private final Widget body;

    private boolean expanded;


    public NavigationPanel(final String name, final Widget body, final Widget buttons, final boolean expanded) {
        this.body = body;

        arrow.setStyleName("navigation-header-arrow");
        arrow.addDomHandler(e -> setExpanded(!this.expanded), ClickEvent.getType());

        final Label text = new Label(name);
        text.setStyleName("navigation-header-text");

        header.setStyleName("navigation-header");
        header.add(arrow);
        header.add(text);
        if (buttons != null) {
            header.add(buttons);
        }
        body.addStyleName("navigation-body");

        final FlowPanel fp = new FlowPanel();
        fp.setStyleName("navigation-container");
        fp.add(header);
        fp.add(body);

        setExpanded(expanded);

        initWidget(fp);
    }

    public void setExpanded(final boolean expanded) {
        this.expanded = expanded;
        body.setVisible(expanded);
        if (expanded) {
            arrow.addStyleName("navigation-header-arrow-open");
        } else {
            arrow.removeStyleName("navigation-header-arrow-open");
        }
    }
}
