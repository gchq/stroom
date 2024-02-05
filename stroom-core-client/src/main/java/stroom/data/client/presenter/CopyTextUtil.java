package stroom.data.client.presenter;

import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class CopyTextUtil {

    public static final String ICON_NAME = "svgIcon";
    public static final String COPY_CLASS_NAME = "docRefLinkCopy";
    private static final Template TEMPLATE;

    static {
        TEMPLATE = GWT.create(Template.class);
    }

    public static SafeHtml render(final String value) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        render(value, sb);
        return sb.toSafeHtml();
    }

    public static void render(final String value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final SafeHtml textSafeHtml = TEMPLATE
                    .div("docRefLinkText", SafeHtmlUtils.fromString(value));

            sb.appendHtmlConstant("<div class=\"docRefLinkContainer\">");
            sb.append(textSafeHtml);

            if (value.trim().length() > 0) {
                final SafeHtml copy = SvgImageUtil.toSafeHtml(SvgImage.COPY, ICON_NAME, COPY_CLASS_NAME);
                sb.append(copy);
            }

            sb.appendHtmlConstant("</div>");
        }
    }

    public static void onClick(final NativeEvent e) {
        if (BrowserEvents.MOUSEDOWN.equals(e.getType()) && MouseUtil.isPrimary(e)) {
            final Element element = e.getEventTarget().cast();
            final Element copy =
                    ElementUtil.findMatching(element, CopyTextUtil.COPY_CLASS_NAME, 0, 5);
            if (copy != null) {
                final Element container =
                        ElementUtil.findMatching(element, "docRefLinkContainer", 0, 5);
                if (container != null && container.getInnerText().trim().length() > 0) {
                    ClipboardUtil.copy(container.getInnerText().trim());
                }
            }
        }
    }

    public static SafeHtml div(String className, SafeHtml content) {
        return TEMPLATE.div(className, content);
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);
    }
}
