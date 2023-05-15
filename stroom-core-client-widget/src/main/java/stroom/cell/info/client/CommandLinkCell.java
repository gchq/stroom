package stroom.cell.info.client;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import static com.google.gwt.dom.client.BrowserEvents.CLICK;

public class CommandLinkCell extends AbstractCell<CommandLink> {

    private static Template template;

    public CommandLinkCell() {
        super(CLICK);

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void onBrowserEvent(Context context, Element parent, CommandLink value,
                               NativeEvent event, ValueUpdater<CommandLink> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);

        if (CLICK.equals(event.getType()) && valueUpdater != null && value.getCommand() != null) {
            valueUpdater.update(value);
        }
    }

    @Override
    public void render(final Context context, final CommandLink value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else if (value.getCommand() != null) {
            sb.append(template.link(value.getText()));
        } else {
            sb.append(template.text(value.getText()));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"CommandLinkCell\">{0}</div>")
        SafeHtml link(String text);

        @Template("{0}")
        SafeHtml text(String text);
    }
}
