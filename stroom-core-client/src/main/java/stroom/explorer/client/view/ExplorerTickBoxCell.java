package stroom.explorer.client.view;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.UriUtils;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.view.client.SelectionModel;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.explorer.client.presenter.TickBoxSelectionModel;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.client.ImageUtil;

public class ExplorerTickBoxCell extends AbstractCell<ExplorerNode> {
    private static Template template;
    private static Resources resources;
    private final SelectionModel<ExplorerNode> selectionModel;
    private TickBoxCell tickBoxCell;
    public ExplorerTickBoxCell(final SelectionModel<ExplorerNode> selectionModel) {
        this.selectionModel = selectionModel;

        if (selectionModel != null && selectionModel instanceof TickBoxSelectionModel) {
            tickBoxCell = TickBoxCell.create(true, false);
        }

        if (template == null) {
            template = GWT.create(Template.class);
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }
    }

    public String getExpanderClassName() {
        return resources.style().expander();
    }

    public String getTickBoxClassName() {
        return resources.style().tickBox();
    }

    @Override
    public void render(final Context context, final ExplorerNode item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final Style style = resources.style();

            int expanderPadding = 4;

            SafeHtml expanderIcon = null;
            if (item.getNodeState() != null) {
                switch (item.getNodeState()) {
                    case LEAF:
                        expanderIcon = SafeHtmlUtils.EMPTY_SAFE_HTML;
                        expanderPadding += 13;
                        break;
                    case OPEN:
                        expanderIcon = template.icon(resources.style().expanderIcon(), UriUtils.fromTrustedString(ImageUtil.getImageURL() + "tree-open.svg"));
                        break;
                    case CLOSED:
                        expanderIcon = template.icon(resources.style().expanderIcon(), UriUtils.fromTrustedString(ImageUtil.getImageURL() + "tree-closed.svg"));
                        break;
                }
            } else {
                expanderIcon = template.icon(resources.style().expanderIcon(), UriUtils.fromTrustedString(ImageUtil.getImageURL() + "tree-leaf.svg"));
            }

            int indent = item.getDepth();
//            if (item.isLeaf()) {
//                indent++;
//            }
            indent = expanderPadding + (indent * 17);

//            final SafeHtml indentHtml = template.indent(style.indent(), indent);

            final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedString("padding-left:" + indent + "px;");
            final SafeHtml expanderHtml = template.expander(style.expander(), paddingLeft, expanderIcon);
            final SafeUri safeUri = UriUtils.fromTrustedString(ImageUtil.getImageURL() + item.getIconUrl());
            final SafeHtml iconHtml = template.icon(style.icon(), safeUri);
            final SafeHtml textHtml = template.text(style.text(), SafeHtmlUtils.fromString(item.getDisplayValue()));

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            content.append(expanderHtml);

            if (tickBoxCell != null) {
                final SafeHtmlBuilder tb = new SafeHtmlBuilder();
                tickBoxCell.render(context, getValue(item), tb);

                final SafeHtml tickBoxHtml = template.tickBox(style.tickBox(), tb.toSafeHtml());
                content.append(tickBoxHtml);
            }

            content.append(iconHtml);
            content.append(textHtml);

            sb.append(template.outer(style.outer(), content.toSafeHtml()));
        }
    }

    private SafeHtml getImageHtml(final ImageResource res) {
        // Get the HTML for the image.
        final AbstractImagePrototype proto = AbstractImagePrototype.create(res);
        final SafeHtml image = SafeHtmlUtils.fromTrustedString(proto.getHTML());
        return image;
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

    public interface Style extends CssResource {
        /**
         * The path to the default CSS styles used by this resource.
         */
        String DEFAULT_CSS = "stroom/explorer/client/view/ExplorerTickBoxCell.css";

        String outer();

        String expander();

        String expanderIcon();

        String tickBox();

        String icon();

        String text();
    }

    interface Resources extends ClientBundle {
        @Source(Style.DEFAULT_CSS)
        Style style();
    }

    interface Template extends SafeHtmlTemplates {
//        @Template("<div class=\"{0}\" style=\"width:{1}px\"></div>")
//        SafeHtml indent(String indentClass, int indent);

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml tickBox(String iconClass, SafeHtml icon);

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
