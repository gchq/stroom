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
import stroom.explorer.shared.ExplorerData;
import stroom.util.client.ImageUtil;

public class ExplorerCell extends AbstractCell<ExplorerData> {
    private static Template template;
    private static Resources resources;

    public ExplorerCell() {
        if (template == null) {
            template = GWT.create(Template.class);
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }
    }

    public String getExpanderClassName() {
        return resources.style().expander();
    }

    @Override
    public void render(final Context context, final ExplorerData item, final SafeHtmlBuilder sb) {
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
                        expanderIcon = getImageHtml(resources.open());
                        break;
                    case CLOSED:
                        expanderIcon = getImageHtml(resources.closed());
                        break;
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
                expanderHtml = template.expander(style.expander(), paddingLeft, expanderIcon);
            }

            if (item.getIconUrl() != null) {
                final SafeUri safeUri = UriUtils.fromTrustedString(ImageUtil.getImageURL() + item.getIconUrl());
                iconHtml = template.icon(style.icon(), safeUri);
            }

            if (item.getDisplayValue() != null) {
                textHtml = template.text(style.text(), SafeHtmlUtils.fromString(item.getDisplayValue()));
            }

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            content.append(expanderHtml);
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

    public interface Style extends CssResource {
        /**
         * The path to the default CSS styles used by this resource.
         */
        String DEFAULT_CSS = "stroom/explorer/client/view/ExplorerCell.css";

        String outer();

        String expander();

        String icon();

        String text();
    }

    interface Resources extends ClientBundle {
        ImageResource open();

        ImageResource closed();

        ImageResource leaf();

        @Source(Style.DEFAULT_CSS)
        Style style();
    }

    interface Template extends SafeHtmlTemplates {
//        @Template("<div class=\"{0}\" style=\"width:{1}px\"></div>")
//        SafeHtml indent(String indentClass, int indent);

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\"><img src=\"{1}\" /></div>")
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
