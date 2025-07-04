package stroom.contentstore.client.presenter;

import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

/**
 * Shows an icon in a DataGrid cell.
 * Note: this does NOT ensure the SVG is safe to display!
 */
public class SvgIconCell extends AbstractCell<String> {

    /**
     * Render the cell.
     * @param context the {@link Context} of the cell
     * @param value the cell value to be rendered
     * @param sb the {@link SafeHtmlBuilder} to be written to
     */
    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String className = "svgCell-icon";

            sb.append(SvgImageUtil.toSafeHtml((String) null, value, className));
        }
    }
}
