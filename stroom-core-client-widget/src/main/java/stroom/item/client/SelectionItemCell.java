package stroom.item.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class SelectionItemCell extends AbstractCell<SelectionItem> {

    private static Template template;

    public SelectionItemCell() {
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    private String getCellClassName() {
        return "queryHelpItemCell";
    }

    @Override
    public void render(final Context context, final SelectionItem row, final SafeHtmlBuilder sb) {
        if (row != null) {
            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            if (row.getIcon() != null) {
                // Add icon
                final SafeHtml iconSafeHtml = SvgImageUtil.toSafeHtml(
                        row.getLabel(),
                        row.getIcon(),
                        "explorerCell-icon");
                content.append(iconSafeHtml);
            }
            content.append(template.div(getCellClassName() + "-text",
                    SafeHtmlUtils.fromString(row.getLabel())));

            // Add parent indicator arrow.
            if (row.isHasChildren()) {
                final SvgImage expanderIcon = SvgImage.ARROW_RIGHT;
                SafeHtml expanderIconSafeHtml;
                String className = getCellClassName() + "-expander";
                className += " " + expanderIcon.getClassName();
                expanderIconSafeHtml = SafeHtmlUtils.fromTrustedString(expanderIcon.getSvg());
                content.append(template.div(className, expanderIconSafeHtml));
            }

            sb.append(template.div("explorerCell", content.toSafeHtml()));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);
    }
}
