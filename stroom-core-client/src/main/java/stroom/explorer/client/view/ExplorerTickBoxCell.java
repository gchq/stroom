package stroom.explorer.client.view;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.explorer.client.presenter.TickBoxSelectionModel;
import stroom.explorer.shared.ExplorerNode;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.SelectionModel;

public class ExplorerTickBoxCell extends AbstractCell<ExplorerNode> {

    private static Template template;
    private final SelectionModel<ExplorerNode> selectionModel;
    private TickBoxCell tickBoxCell;

    public ExplorerTickBoxCell(final SelectionModel<ExplorerNode> selectionModel) {
        this.selectionModel = selectionModel;

        if (selectionModel != null && selectionModel instanceof TickBoxSelectionModel) {
            tickBoxCell = TickBoxCell.create(true, false);
        }

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    public String getExpanderClassName() {
        return "explorerTickBoxCell-expander";
    }

    public String getTickBoxClassName() {
        return "explorerTickBoxCell-tickBox";
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
                        expanderIcon =
                                template.icon("svgIcon  explorerCell-treeOpen");
                        break;
                    case CLOSED:
                        expanderIcon =
                                template.icon("svgIcon explorerCell-treeClosed");
                        break;
                    default:
                        throw new RuntimeException("Unexpected state " + item.getNodeState());
                }
            } else {
                expanderIcon = template.icon("svgIcon explorerCell-treeLeaf");
            }

            int indent = item.getDepth();
//            if (item.isLeaf()) {
//                indent++;
//            }
            indent = expanderPadding + (indent * 17);

//            final SafeHtml indentHtml = template.indent(style.indent(), indent);

            final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedString("padding-left:" + indent + "px;");
            final SafeHtml expanderHtml =
                    template.expander("explorerTickBoxCell-expander", paddingLeft, expanderIcon);
            final SafeHtml iconHtml = template.icon("explorerTickBoxCell-icon " + item.getIconClassName());
            final SafeHtml textHtml = template.text("explorerTickBoxCell-text",
                    SafeHtmlUtils.fromString(item.getDisplayValue()));

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            content.append(expanderHtml);

            if (tickBoxCell != null) {
                final SafeHtmlBuilder tb = new SafeHtmlBuilder();
                tickBoxCell.render(context, getValue(item), tb);

                final SafeHtml tickBoxHtml = template.tickBox("explorerTickBoxCell-tickBox", tb.toSafeHtml());
                content.append(tickBoxHtml);
            }

            content.append(iconHtml);
            content.append(textHtml);

            sb.append(template.outer("explorerTickBoxCell-outer", content.toSafeHtml()));
        }
    }

    private TickBoxState getValue(final ExplorerNode item) {
        if (selectionModel == null) {
            return TickBoxState.UNTICK;
        } else if (selectionModel instanceof TickBoxSelectionModel) {
            final TickBoxSelectionModel tickBoxSelectionModel = (TickBoxSelectionModel) selectionModel;
            return tickBoxSelectionModel.getState(item);
        } else {
            if (selectionModel.isSelected(item)) {
                return TickBoxState.TICK;
            } else {
                return TickBoxState.UNTICK;
            }
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml tickBox(String iconClass, SafeHtml icon);

        @Template("<div class=\"{0}\"></div>")
        SafeHtml icon(String iconClass);

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
