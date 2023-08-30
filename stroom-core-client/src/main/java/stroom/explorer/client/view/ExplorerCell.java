package stroom.explorer.client.view;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.explorer.client.presenter.TickBoxSelectionModel;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeInfo;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.Severity;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.SelectionModel;

import java.util.stream.Collectors;

public class ExplorerCell extends AbstractCell<ExplorerNode> {

    private static Template template;
    private final SelectionModel<ExplorerNode> selectionModel;
    private TickBoxCell tickBoxCell;

    public ExplorerCell(final SelectionModel<ExplorerNode> selectionModel) {
        this.selectionModel = selectionModel;

        if (selectionModel != null && selectionModel instanceof TickBoxSelectionModel) {
            tickBoxCell = TickBoxCell.create(true, false);
        }

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    private String getCellClassName() {
        return "explorerCell";
    }

    public String getExpanderClassName() {
        return getCellClassName() + "-expander";
    }

    public String getTickBoxClassName() {
        return getCellClassName() + "-tickBox";
    }

    @Override
    public void render(final Context context, final ExplorerNode item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final SafeHtmlBuilder content = new SafeHtmlBuilder();

            int expanderPadding = 4;

            SvgImage expanderIcon = null;
            if (item.getNodeState() != null) {
                switch (item.getNodeState()) {
                    case LEAF:
                        break;
                    case OPEN:
                        expanderIcon = SvgImage.ARROW_DOWN;
                        break;
                    case CLOSED:
                        expanderIcon = SvgImage.ARROW_RIGHT;
                        break;
                    default:
                        throw new RuntimeException("Unexpected state " + item.getNodeState());
                }
            }

            int indent = item.getDepth();
            indent = expanderPadding + (indent * 17);
            final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedString("padding-left:" + indent + "px;");

            // Add expander.
            SafeHtml expanderIconSafeHtml;
            String className = getCellClassName() + "-expander";
            if (expanderIcon != null) {
                expanderIconSafeHtml = SafeHtmlUtils.fromTrustedString(expanderIcon.getSvg());
                className += " " + expanderIcon.getClassName();
            } else {
                expanderIconSafeHtml = SvgImageUtil.emptySvg();
            }

            content.append(template.expander(
                    className,
                    paddingLeft,
                    expanderIconSafeHtml));

            if (tickBoxCell != null) {
                final SafeHtmlBuilder tb = new SafeHtmlBuilder();
                tickBoxCell.render(context, getValue(item), tb);

                final SafeHtml tickBoxHtml = template.div(getCellClassName() + "-tickBox", tb.toSafeHtml());
                // Add tickbox
                content.append(tickBoxHtml);
            }

            if (item.getIcon() != null) {
                // Add icon
                final SafeHtml iconSafeHtml = SvgImageUtil.toSafeHtml(
                        item.getType(),
                        item.getIcon(),
                        getCellClassName() + "-icon");
                final SafeHtmlBuilder builder = new SafeHtmlBuilder().append(iconSafeHtml);

                item.getMaxSeverity().ifPresent(maxSeverity -> {
                    final SvgImage svgImage = maxSeverity.greaterThanOrEqual(Severity.WARNING)
                            ? SvgImage.ALERT_SIMPLE
                            : SvgImage.INFO;
                    builder.append(SvgImageUtil.toSafeHtml(
                            buildAlertText(item),
                            svgImage,
                            "svgIcon", "small", getCellClassName() + "-alert-icon"));
                });

                content.append(template.div(
                        getCellClassName() + "-icon-wrapper",
                        builder.toSafeHtml()));
            }

            if (item.getDisplayValue() != null) {
                // Add text
                content.append(template.div(getCellClassName() + "-text",
                        SafeHtmlUtils.fromString(item.getDisplayValue())));
            }

            // If the item is a favourite and not part of the Favourites node, display a star next to it
            if (item.getIsFavourite() && item.getRootNodeUuid() != null &&
                    !ExplorerConstants.FAVOURITES_DOC_REF.getUuid().equals(item.getRootNodeUuid())) {
                content.append(SvgImageUtil.toSafeHtml(
                        "Item is a favourite",
                        SvgImage.FAVOURITES,
                        "svgIcon", "small"));
            }

//            item.getMaxSeverity().ifPresent(maxSeverity -> {
//                content.append(SvgImageUtil.toSafeHtml(
//                        buildAlertText(item),
//                        SvgImage.ALERT,
//                        "svgIcon", "small"));
//            });

            sb.append(template.outer(content.toSafeHtml()));
        }
    }

    private String buildAlertText(final ExplorerNode explorerNode) {
        return GwtNullSafe.get(
                explorerNode,
                ExplorerNode::getNodeInfoList,
                nodeInfoList -> GwtNullSafe.stream(nodeInfoList)
                        .sorted()
                        .map(NodeInfo::toString)
                        .collect(Collectors.joining("\n")));
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


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);

        @Template("<div class=\"explorerCell\">{0}</div>")
        SafeHtml outer(SafeHtml content);
    }
}
