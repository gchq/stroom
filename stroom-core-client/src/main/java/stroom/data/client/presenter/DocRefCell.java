package stroom.data.client.presenter;

import stroom.data.grid.client.EventCell;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.web.bindery.event.shared.EventBus;

import static com.google.gwt.dom.client.BrowserEvents.MOUSEDOWN;

public class DocRefCell extends AbstractCell<DocRef> implements HasHandlers, EventCell {

    private static final String ICON_NAME = "svgIcon";
    private static final String COPY_CLASS_NAME = "docRefLinkCopy";
    private static final String OPEN_CLASS_NAME = "docRefLinkOpen";

    private final EventBus eventBus;
    private final boolean allowLinkByName;
    private static volatile Template template;

    public DocRefCell(final EventBus eventBus, final boolean allowLinkByName) {
        super(MOUSEDOWN);
        this.eventBus = eventBus;
        this.allowLinkByName = allowLinkByName;

        if (template == null) {
            synchronized (DocRefCell.class) {
                if (template == null) {
                    template = GWT.create(Template.class);
                }
            }
        }
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, COPY_CLASS_NAME, 0, 5) ||
                    ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 0, 5);
        }
        return false;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final DocRef value,
                               final NativeEvent event,
                               final ValueUpdater<DocRef> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if (MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
            onEnterKeyDown(context, parent, value, event, valueUpdater);
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final DocRef value,
                                  final NativeEvent event,
                                  final ValueUpdater<DocRef> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        if (ElementUtil.hasClassName(element, COPY_CLASS_NAME, 0, 5)) {
            final String text = getText(value);
            if (text != null) {
                ClipboardUtil.copy(text);
            }
        } else if (ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 0, 5)) {
            OpenDocumentEvent.fire(this, value, true);
        }
    }

    @Override
    public void render(final Context context, final DocRef value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String text = getText(value);
            if (text == null) {
                sb.append(SafeHtmlUtils.fromSafeConstant("Not visible"));
            } else {
                final SafeHtml textSafeHtml = template
                        .div("docRefLinkText", SafeHtmlUtils.fromString(text));

                sb.appendHtmlConstant("<div class=\"docRefLinkContainer\">");
                sb.append(textSafeHtml);

                final SafeHtml copy = SvgImageUtil.toSafeHtml(SvgImage.COPY, ICON_NAME, COPY_CLASS_NAME);
                sb.append(template.divWithToolTip(
                        "Copy name '" + value.getName() + "' to clipboard.",
                        copy));

                if (value.getUuid() != null || allowLinkByName) {
                    final SafeHtml open = SvgImageUtil.toSafeHtml(SvgImage.OPEN, ICON_NAME, OPEN_CLASS_NAME);
                    sb.append(template.divWithToolTip(
                            "Open " + value.getType() + " " + value.getName() + " in new tab.",
                            open));
                }

                sb.appendHtmlConstant("</div>");
            }
        }
    }

    private String getText(final DocRef docRef) {
        if (!GwtNullSafe.isBlankString(docRef.getName())) {
            return docRef.getName();
        } else if (!GwtNullSafe.isBlankString(docRef.getUuid())) {
            return docRef.getUuid();
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String expanderClass, SafeHtml icon);

        @Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithToolTip(String title, SafeHtml content);
    }
}
