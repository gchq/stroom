package stroom.explorer.client.view;

import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ExplorerCell extends AbstractCell<ExplorerNode> {

    private static Template template;

    public ExplorerCell() {
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    public String getExpanderClassName() {
        return "explorerCell-expanderIcon";
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
                        expanderIcon = template.icon(
                                "svgIcon explorerCell-expanderIcon explorerCell-treeOpen",
                                item.getType());
                        break;
                    case CLOSED:
                        expanderIcon = template.icon(
                                "svgIcon explorerCell-expanderIcon explorerCell-treeClosed",
                                item.getType());
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
            SafeHtml favIconHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;

            if (expanderIcon != null) {
                final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedString("padding-left:" + indent + "px;");
                expanderHtml = template.expander("explorerCell-expander", paddingLeft, expanderIcon);
            }

            if (item.getIconClassName() != null) {
                iconHtml = template.icon("explorerCell-icon " + item.getIconClassName(), item.getType());
            }

            if (item.getDisplayValue() != null) {
                textHtml = template.text("explorerCell-text",
                        SafeHtmlUtils.fromString(item.getDisplayValue()));
            }

            // If the item is a favourite and not part of the Favourites node, display a star next to it
            if (item.getIsFavourite() && !ExplorerConstants.FAVOURITES_DOC_REF.equals(item.getParent().getDocRef())) {
                favIconHtml = template.favIcon(
                        DocumentType.DOC_IMAGE_CLASS_NAME + ExplorerConstants.FAVOURITES,
                        "Item is a favourite");
            }

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            content.append(expanderHtml);
            content.append(iconHtml);
            content.append(textHtml);
            content.append(favIconHtml);

            sb.append(template.outer("explorerCell-outer", content.toSafeHtml()));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\" title=\"{1}\"></div>")
        SafeHtml icon(String iconClass, String title);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml text(String textClass, SafeHtml text);

        @Template("<div class=\"{0}\" title=\"{1}\"></div>")
        SafeHtml favIcon(String iconClass, String title);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml outer(String outerClass, SafeHtml content);

        /**
         * The wrapper around the image vertically aligned to the middle.
         */
        @Template("")
        SafeHtml imageWrapperMiddle(SafeStyles styles, SafeHtml image);
    }
}
