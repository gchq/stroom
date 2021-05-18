package stroom.explorer.client.view;

import stroom.explorer.shared.ExplorerNode;
import stroom.util.client.ImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.UriUtils;

public class ExplorerCell extends AbstractCell<ExplorerNode> {

    private static Template template;

    public ExplorerCell() {
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    public String getExpanderClassName() {
        return "expanderCell-expander";
    }

    @Override
    public void render(final Context context, final ExplorerNode item, final SafeHtmlBuilder sb) {
        if (item != null) {

            int expanderPadding = 4;

            SafeHtml expanderIcon = null;
            if (item.getNodeState() != null) {
                switch (item.getNodeState()) {
                    case LEAF:
                        expanderIcon = SafeHtmlUtils.EMPTY_SAFE_HTML;
                        expanderPadding += 13;
                        break;
                    case OPEN:
                        expanderIcon = template.node(
                                "explorerCell-expanderIcon explorerCell-treeOpen");
                        break;
                    case CLOSED:
                        expanderIcon = template.node(
                                "explorerCell-expanderIcon explorerCell-treeClosed");
                        break;
                    default:
                        throw new RuntimeException("Unexpected state " + item.getNodeState());
                }
            }
//            else {
//                expanderIcon = getImageHtml(resources.leaf());
//            }

            int indent = item.getDepth();
//            if (item.isLeaf()) {
//                indent++;
//            }
            indent = expanderPadding + (indent * 17);

//            final SafeHtml indentHtml = template.indent(style.indent(), indent);

            SafeHtml expanderHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;
            SafeHtml iconHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;
            SafeHtml textHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;

            if (expanderIcon != null) {
                final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedString("padding-left:" + indent + "px;");
                expanderHtml = template.expander("explorerCell-expander", paddingLeft, expanderIcon);
            }

            if (item.getIconUrl() != null) {
                final SafeUri safeUri = UriUtils.fromTrustedString(ImageUtil.getImageURL() + item.getIconUrl());
                iconHtml = template.icon("explorerCell-icon", safeUri);
            }

            if (item.getDisplayValue() != null) {
                textHtml = template.text("explorerCell-text", SafeHtmlUtils.fromString(item.getDisplayValue()));
            }

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            content.append(expanderHtml);
            content.append(iconHtml);
            content.append(textHtml);

            sb.append(template.outer("explorerCell-outer", content.toSafeHtml()));
        }
    }

    interface Template extends SafeHtmlTemplates {
//        @Template("<div class=\"{0}\" style=\"width:{1}px\"></div>")
//        SafeHtml indent(String indentClass, int indent);

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\" />")
        SafeHtml node(String iconClass);

        @Template("<img class=\"{0}\" src=\"{1}\" />")
        SafeHtml icon(String iconClass, SafeUri iconUrl);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml text(String textClass, SafeHtml text);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml outer(String outerClass, SafeHtml content);

        /**
         * The wrapper around the image vertically aligned to the middle.
         */
        @Template("")
        SafeHtml imageWrapperMiddle(SafeStyles styles, SafeHtml image);
    }
}
