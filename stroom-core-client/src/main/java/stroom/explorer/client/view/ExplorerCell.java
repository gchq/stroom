/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.explorer.client.view;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.client.presenter.TickBoxSelectionModel;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeInfo;
import stroom.explorer.shared.NodeFlag;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
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

    private static final String DESCENDANT_ISSUES_MSG = "Descendants have issues";

    private static Template template;
    private final SelectionModel<ExplorerNode> selectionModel;
    private TickBoxCell tickBoxCell;
    private boolean showAlerts;

    public ExplorerCell(final SelectionModel<ExplorerNode> selectionModel, final boolean showAlerts) {
        this.selectionModel = selectionModel;
        this.showAlerts = showAlerts;

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

    public void setShowAlerts(final boolean showAlerts) {
        this.showAlerts = showAlerts;
    }

    @Override
    public void render(final Context context, final ExplorerNode node, final SafeHtmlBuilder sb) {
        if (node != null) {
            final SafeHtmlBuilder content = new SafeHtmlBuilder();

            final int expanderPadding = 4;

            SvgImage expanderIcon = null;
            if (node.hasNodeFlag(NodeFlag.OPEN)) {
                expanderIcon = SvgImage.ARROW_DOWN;
            } else if (node.hasNodeFlag(NodeFlag.CLOSED)) {
                expanderIcon = SvgImage.ARROW_RIGHT;
            }

            int indent = node.getDepth();
            indent = expanderPadding + (indent * 17);
            final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedString("padding-left:" + indent + "px;");

            // Add expander.
            final SafeHtml expanderIconSafeHtml;
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
                tickBoxCell.render(context, getValue(node), tb);

                final SafeHtml tickBoxHtml = template.div(getCellClassName() + "-tickBox", tb.toSafeHtml());
                // Add tickbox
                content.append(tickBoxHtml);
            }

            final DocumentType documentType = DocumentTypeRegistry.get(node.getType());
            if (documentType != null && documentType.getIcon() != null) {
                // Add icon
                final SafeHtml iconSafeHtml = SvgImageUtil.toSafeHtml(
                        documentType.getDisplayType(),
                        documentType.getIcon(),
                        getCellClassName() + "-icon");
                final SafeHtmlBuilder builder = new SafeHtmlBuilder().append(iconSafeHtml);

                if (showAlerts) {
                    if (node.hasDescendantNodeInfo()) {
                        builder.append(SvgImageUtil.toSafeHtml(
                                buildAlertText(node),
                                SvgImage.ALERT_SIMPLE,
                                "svgIcon", "small", getCellClassName() + "-alert-icon"));
                    }
                }

                content.append(template.div(
                        getCellClassName() + "-icon-wrapper",
                        builder.toSafeHtml()));
            }

            if (node.getDisplayValue() != null) {
                // Add text
                final SafeHtml text = SafeHtmlUtils.fromString(node.getDisplayValue());
                content.append(template.text(getCellClassName() + "-text", text.asString(), text));
            }

            // If the item is a favourite and not part of the Favourites node, display a star next to it
            if (node.hasNodeFlag(NodeFlag.FAVOURITE) && node.getRootNodeUuid() != null &&
                    !ExplorerConstants.FAVOURITES_DOC_REF.getUuid().equals(node.getRootNodeUuid())) {
                content.append(SvgImageUtil.toSafeHtml(
                        "Item is a favourite",
                        SvgImage.FAVOURITES,
                        "svgIcon", "small"));
            }

            // We style the non-matches rather than the matches
            final String filterMatchClass;
            if (node.hasNodeFlag(NodeFlag.FILTER_MATCH)) {
                filterMatchClass = getCellClassName() + "-" + "filter-match";
            } else if (node.hasNodeFlag(NodeFlag.FILTER_NON_MATCH)) {
                filterMatchClass = getCellClassName() + "-" + "filter-no-match";
            } else {
                filterMatchClass = "";
            }

            sb.append(template.outer(filterMatchClass, content.toSafeHtml()));
        }
    }

    private String buildAlertText(final ExplorerNode explorerNode) {
        if (explorerNode == null) {
            return null;
        } else {
            if (explorerNode.hasNodeFlag(NodeFlag.DESCENDANT_NODE_INFO)) {
                return DESCENDANT_ISSUES_MSG;
            } else if (explorerNode.hasNodeInfo()) {
                return NullSafe.get(
                        explorerNode,
                        ExplorerNode::getNodeInfoList,
                        nodeInfoList -> NullSafe.stream(nodeInfoList)
                                .sorted()
                                .map(NodeInfo::toString)
                                .collect(Collectors.joining("\n")));
            } else {
                return null;
            }
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


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml text(String className, String title, SafeHtml content);

        @Template("<div class=\"explorerCell {0}\">{1}</div>")
        SafeHtml outer(String extraClassName, SafeHtml content);
    }
}
