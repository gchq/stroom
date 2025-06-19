package stroom.data.client.presenter;

import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;

public class CopyTextUtil {

    public static final String ICON_NAME = "svgIcon";
    public static final String COPY_CLASS_NAME = "docRefLinkCopy";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";
    private static final String HOVER_ICON_CLASS_NAME = "hoverIcon";
    private static final Template TEMPLATE;
    private static final int TRUNCATE_THRESHOLD = 30;

    static {
        TEMPLATE = GWT.create(Template.class);
    }

    public static SafeHtml render(final String value) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        render(value, sb);
        return sb.toSafeHtml();
    }

    public static void render(final String value,
                              final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            render(value, SafeHtmlUtils.fromString(value), sb);
        }
    }

    public static void render(final String value,
                              final SafeHtml safeHtml,
                              final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final SafeHtml textSafeHtml = TEMPLATE
                    .div("docRefLinkText", safeHtml);

            final String containerClasses = String.join(
                    " ",
                    HOVER_ICON_CONTAINER_CLASS_NAME,
                    "docRefLinkContainer");
            sb.appendHtmlConstant("<div class=\"" + containerClasses + "\">");
            sb.append(textSafeHtml);

            NullSafe.consumeNonBlankString(value, str -> {
                final SafeHtml copy = SvgImageUtil.toSafeHtml(
                        SvgImage.COPY,
                        ICON_NAME,
                        COPY_CLASS_NAME,
                        HOVER_ICON_CLASS_NAME);
                // Cell values could be huge so truncate big ones
                final String truncatedStr = str.length() > TRUNCATE_THRESHOLD
                        ? (str.substring(0, TRUNCATE_THRESHOLD) + "...")
                        : str;
                sb.append(TEMPLATE.divWithToolTip(
                        "Copy value '" + truncatedStr + "' to clipboard",
                        copy));
            });

            sb.appendHtmlConstant("</div>");
        }
    }

    public static void onClick(final NativeEvent e,
                               final HasHandlers hasHandlers) {
        if (BrowserEvents.MOUSEDOWN.equals(e.getType())) {
            final Element element = e.getEventTarget().cast();
            final Element container = ElementUtil.findParent(
                    element, "docRefLinkContainer", 5);
            final String text = NullSafe.get(container, Element::getInnerText);
            if (NullSafe.isNonBlankString(text)) {
                if (MouseUtil.isPrimary(e)) {
                    final Element copy = ElementUtil.findParent(element, CopyTextUtil.COPY_CLASS_NAME, 5);
                    if (copy != null) {
                        ClipboardUtil.copy(text.trim());
                    }
                } else {
                    final List<Item> menuItems = new ArrayList<>();
                    menuItems.add(new IconMenuItem.Builder()
                            .priority(1)
                            .icon(SvgImage.COPY)
                            .text("Copy")
                            .command(() -> ClipboardUtil.copy(text.trim()))
                            .build());
                    ShowMenuEvent
                            .builder()
                            .items(menuItems)
                            .popupPosition(new PopupPosition(e.getClientX(), e.getClientY()))
                            .fire(hasHandlers);
                }
            }
        }
    }

    public static SafeHtml div(final String className, final SafeHtml content) {
        return TEMPLATE.div(className, content);
    }

    // --------------------------------------------------------------------------------

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);

        @Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithToolTip(String title, SafeHtml content);
    }
}
