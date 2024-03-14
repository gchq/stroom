package stroom.cell.info.client;

import stroom.util.shared.GwtNullSafe;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ColourSwatchCell extends AbstractCell<String> {

    public ColourSwatchCell() {
    }

    @Override
    public void render(final Context context,
                       final String cssColour,
                       final SafeHtmlBuilder sb) {

        if (GwtNullSafe.isBlankString(cssColour)) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            sb.appendHtmlConstant("<div class=\"colourSwatchCell colourSwatchCell-container\">");

            sb.appendHtmlConstant("<div class=\"colourSwatchCell-swatch\" style=\"background-color:");
            sb.appendEscaped(cssColour);
            sb.appendHtmlConstant("\">");
            sb.appendHtmlConstant("</div>");

            sb.appendHtmlConstant("<div class=\"colourSwatchCell-text\">");
            sb.appendEscaped(cssColour);
            sb.appendHtmlConstant("</div>");

            sb.appendHtmlConstant("</div>");
        }
    }
}
