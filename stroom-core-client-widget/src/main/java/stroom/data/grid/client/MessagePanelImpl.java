package stroom.data.grid.client;

import stroom.util.shared.ErrorMessage;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;

import java.util.List;

public class MessagePanelImpl extends SimplePanel implements MessagePanel {

    private Label message;
    private boolean visible;

    public MessagePanelImpl() {
        setStyleName("dashboardVis-messageOuter");
        setVisible(false);
    }

    @Override
    public void showMessage(final List<ErrorMessage> errors) {
        if (errors != null && errors.size() > 0) {
            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            for (final ErrorMessage error : errors) {
                final String[] lines = error.getMessage().split("\n");
                for (final String line : lines) {
                    sb.appendEscaped(line);
                    sb.appendHtmlConstant("<br />");
                }
            }
            showMessage(sb.toSafeHtml());
        }
    }

    @Override
    public void showMessage(final String message) {
        showMessage(SafeHtmlUtil.from(message));
    }

    @Override
    public void showMessage(final SafeHtml msg) {
        if (!visible) {
            if (message == null) {
                message = new Label("", false);
                message.setStyleName("dashboardVis-messageInner");
                add(message);
            }

            message.getElement().setInnerSafeHtml(msg);
            setVisible(true);
            visible = true;
        }
    }

    @Override
    public void hideMessage() {
        if (visible) {
            setVisible(false);
            visible = false;
        }
    }
}
