package stroom.data.client.presenter;

import stroom.data.grid.client.EventCell;
import stroom.util.client.ClipboardUtil;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.web.bindery.event.shared.EventBus;

import static com.google.gwt.dom.client.BrowserEvents.MOUSEDOWN;

public class CopyTextCell extends AbstractCell<String> implements HasHandlers, EventCell {

    private final EventBus eventBus;

    public CopyTextCell(final EventBus eventBus) {
        super(MOUSEDOWN);
        this.eventBus = eventBus;
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 5);
        }
        return false;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final String value,
                               final NativeEvent event,
                               final ValueUpdater<String> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if (MOUSEDOWN.equals(event.getType())) {
            if (MouseUtil.isPrimary(event)) {
                onEnterKeyDown(context, parent, value, event, valueUpdater);
            }
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final String value,
                                  final NativeEvent event,
                                  final ValueUpdater<String> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        if (ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 5)) {
            if (value != null) {
                ClipboardUtil.copy(value);
            }
        }
    }

    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        CopyTextUtil.render(value, new HtmlBuilder(sb), false);
    }
}
