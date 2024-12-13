package stroom.data.client.presenter;

import stroom.data.client.presenter.DocRefCell.DocRefProvider;
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

import java.util.Objects;
import java.util.function.Function;

import static com.google.gwt.dom.client.BrowserEvents.MOUSEDOWN;

public class DocRefCell<T_ROW> extends AbstractCell<DocRefProvider<T_ROW>>
        implements HasHandlers, EventCell {

    private static final String ICON_CLASS_NAME = "svgIcon";
    private static final String COPY_CLASS_NAME = "docRefLinkCopy";
    private static final String OPEN_CLASS_NAME = "docRefLinkOpen";

    private final EventBus eventBus;
    private final boolean allowLinkByName;
    private final Function<T_ROW, String> cssClassFunction;
    private final Function<DocRefProvider<T_ROW>, SafeHtml> cellTextFunction;

    private static volatile Template template;

    public DocRefCell(final EventBus eventBus,
                      final boolean allowLinkByName) {
        this(eventBus, allowLinkByName, row -> "", null);
    }

    public DocRefCell(final EventBus eventBus,
                      final boolean allowLinkByName,
                      final Function<T_ROW, String> cssClassFunction) {
        this(eventBus, allowLinkByName, cssClassFunction, null);
    }

    public DocRefCell(final EventBus eventBus,
                      final boolean allowLinkByName,
                      final Function<T_ROW, String> cssClassFunction,
                      final Function<DocRefProvider<T_ROW>, SafeHtml> cellTextFunction) {
        super(MOUSEDOWN);
        this.eventBus = eventBus;
        this.allowLinkByName = allowLinkByName;
        this.cssClassFunction = cssClassFunction;
        this.cellTextFunction = GwtNullSafe.requireNonNullElseGet(
                cellTextFunction,
                () -> this::getTextFromDocRef);

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, COPY_CLASS_NAME, 5) ||
                    ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5);
        }
        return false;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final DocRefProvider<T_ROW> value,
                               final NativeEvent event,
                               final ValueUpdater<DocRefProvider<T_ROW>> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if (value.getDocRef() != null) {
            if (MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
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
                                  final DocRefProvider<T_ROW> value,
                                  final NativeEvent event,
                                  final ValueUpdater<DocRefProvider<T_ROW>> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        final DocRef docRef = GwtNullSafe.get(value, DocRefProvider::getDocRef);
        if (docRef != null) {
            if (ElementUtil.hasClassName(element, COPY_CLASS_NAME, 5)) {
                final String text = getTextFromDocRef(docRef);
                if (text != null) {
                    ClipboardUtil.copy(text);
                }
            } else if (ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5)) {
                OpenDocumentEvent.fire(this, docRef, true);
            }
        }
    }

    @Override
    public void render(final Context context, final DocRefProvider<T_ROW> value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final SafeHtml cellText = cellTextFunction.apply(value);
            final DocRef docRef = value.getDocRef();
            String cssClasses = "docRefLinkText";
            if (cssClassFunction != null) {
                final String additionalClasses = cssClassFunction.apply(value.getRow());
                if (additionalClasses != null) {
                    cssClasses += " " + additionalClasses;
                }
            }
            final SafeHtml textDiv = template.div(cssClasses, cellText);

            sb.appendHtmlConstant("<div class=\"docRefLinkContainer\">");
            sb.append(textDiv);

            // This DocRefCell gets used for pipeline props which sometimes are a docRef
            // and other times just a simple string
            if (docRef != null) {
                final SafeHtml copy = SvgImageUtil.toSafeHtml(SvgImage.COPY, ICON_CLASS_NAME, COPY_CLASS_NAME);
                sb.append(template.divWithToolTip(
                        "Copy name '" + docRef.getName() + "' to clipboard",
                        copy));

                if (docRef.getUuid() != null || allowLinkByName) {
                    final SafeHtml open = SvgImageUtil.toSafeHtml(SvgImage.OPEN, ICON_CLASS_NAME, OPEN_CLASS_NAME);
                    sb.append(template.divWithToolTip(
                            "Open " + docRef.getType() + " " + docRef.getName() + " in new tab",
                            open));
                }
            }

            sb.appendHtmlConstant("</div>");
        }
    }

    private SafeHtml getTextFromDocRef(final DocRefProvider<T_ROW> docRefProvider) {
        final DocRef docRef = docRefProvider.getDocRef();
        return GwtNullSafe.getOrElseGet(
                docRef,
                DocRefCell::getTextFromDocRef,
                SafeHtmlUtils::fromString,
                () -> SafeHtmlUtils.EMPTY_SAFE_HTML);
    }

    public static String getTextFromDocRef(final DocRef docRef) {
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
        SafeHtml div(String cssClass, SafeHtml content);

        @Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithToolTip(String title, SafeHtml content);
    }


    // --------------------------------------------------------------------------------


    public static class DocRefProvider<T_ROW> {

        private final T_ROW row;
        private final Function<T_ROW, DocRef> docRefExtractor;

        public DocRefProvider(final T_ROW row,
                              final Function<T_ROW, DocRef> docRefExtractor) {
            this.row = row;
            this.docRefExtractor = Objects.requireNonNull(docRefExtractor);
        }

        /**
         * For uses where the rendering of the cell doesn't need the original row value, so
         * this essentially returns an identity function.
         */
        public static DocRefProvider<DocRef> forDocRef(final DocRef docRefRow) {
            return new DocRefProvider<>(docRefRow, Function.identity());
        }

        public T_ROW getRow() {
            return row;
        }

        public DocRef getDocRef() {
            return GwtNullSafe.get(row, docRefExtractor);
        }
    }
}
