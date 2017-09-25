package stroom.cell.clickable.client;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ClickableSafeHtmlCell extends AbstractCell<ClickableSafeHtml> {
    private static final Set<String> ENABLED_EVENTS = new HashSet<>(Arrays.asList("click"));

    private Consumer<UrlDetector.Hyperlink> urlClickHandler;

    public ClickableSafeHtmlCell(final Consumer<UrlDetector.Hyperlink> urlClickHandler) {
        super(ENABLED_EVENTS);

        this.urlClickHandler = urlClickHandler;
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final ClickableSafeHtml value,
                               final NativeEvent event, final ValueUpdater<ClickableSafeHtml> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);

        if (null == value.getUrl()) {
            return;
        }

        if ("click".equals(event.getType())) {
            final EventTarget eventTarget = event.getEventTarget();
            if (!Element.is(eventTarget)) {
                return;
            }
            if (parent.getFirstChildElement().isOrHasChild(Element.as(eventTarget))) {
                // Ignore clicks that occur outside of the main element.
                urlClickHandler.accept(value.getUrl());
            }
        }
    }

    @Override
    public void render(Context context, ClickableSafeHtml value, SafeHtmlBuilder sb) {
        if (value != null) {
            sb.append(value.getSafeHtml());
        }
    }
}
