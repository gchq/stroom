package stroom.data.client.presenter;

import stroom.data.grid.client.EventCell;
import stroom.util.client.ClipboardUtil;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.view.client.CellPreviewEvent;

import static com.google.gwt.dom.client.BrowserEvents.MOUSEDOWN;

public class CopyTextCell extends AbstractCell<String> implements EventCell {


    public CopyTextCell() {
        super(MOUSEDOWN);
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 0, 5);
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
        if (MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
            onEnterKeyDown(context, parent, value, event, valueUpdater);
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final String value,
                                  final NativeEvent event,
                                  final ValueUpdater<String> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        if (ElementUtil.hasClassName(element, CopyTextUtil.COPY_CLASS_NAME, 0, 5)) {
            if (value != null) {
                ClipboardUtil.copy(value);
            }
        }
    }

    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        CopyTextUtil.render(value, sb);
    }
}
