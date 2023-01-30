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

public class HyperlinkCell extends AbstractCell<String> {

    private static Template template;

    public HyperlinkCell() {
        super(CLICK);

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void onBrowserEvent(Context context, Element parent, String value,
                               NativeEvent event, ValueUpdater<String> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);

        if (CLICK.equals(event.getType()) && valueUpdater != null) {
            valueUpdater.update(value);
        }
    }

    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            sb.append(template.text(value));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"hyperlinkCell\">{0}</div>")
        SafeHtml text(String text);
    }
}
