package stroom.query.client.presenter;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Set;

public class QueryHelpItemCell extends AbstractCell<QueryHelpItem> {

    private static Template template;
    private final Set<QueryHelpItem> openItems;

    public QueryHelpItemCell(final Set<QueryHelpItem> openItems) {
        this.openItems = openItems;
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    private String getCellClassName() {
        return "queryHelpItemCell";
    }

    @Override
    public void render(final Context context, final QueryHelpItem item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final SafeHtmlBuilder content = new SafeHtmlBuilder();

            int expanderPadding = 4;

            SvgImage expanderIcon = null;
            if (item.hasChildren()) {
                final boolean open = openItems.contains(item);
                if (open) {
                    expanderIcon = SvgImage.ARROW_DOWN;
                } else {
                    expanderIcon = SvgImage.ARROW_RIGHT;
                }
            }

            int indent = item.getDepth();
            indent = expanderPadding + (indent * 17);
            final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedString("padding-left:" + indent + "px;");

            // Add expander.
            SafeHtml expanderIconSafeHtml;
            String className = getCellClassName() + "-expander";
            if (expanderIcon != null) {
                className += " " + expanderIcon.getClassName();
                expanderIconSafeHtml = SafeHtmlUtils.fromTrustedString(expanderIcon.getSvg());
            } else {
                expanderIconSafeHtml = SvgImageUtil.emptySvg();
            }
            content.append(template.expander(className, paddingLeft, expanderIconSafeHtml));

            if (item.getLabel() != null) {
                // Add text
                content.append(template.div(getCellClassName() + "-text",
                        item.getLabel()));
            }

            sb.append(template.outer(content.toSafeHtml()));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);

        @Template("<div class=\"explorerCell\">{0}</div>")
        SafeHtml outer(SafeHtml content);
    }
}
