package stroom.cell.info.client;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class RedGreenTextCell extends AbstractCell<Boolean> {

    private static Template template;

    private final String greenText;
    private final String redText;

    public RedGreenTextCell(final String greenText, final String redText) {
        this.greenText = greenText;
        this.redText = redText;
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void render(final Context context,
                       final Boolean isGreen,
                       final SafeHtmlBuilder sb) {
        if (isGreen == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String text = isGreen
                    ? greenText
                    : redText;
            final String className = isGreen
                    ? "redGreenCell__green"
                    : "redGreenCell__red";
            sb.append(template.span(text, className));
        }
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<span class=\"redGreenCell {1}\">{0}</span>")
        SafeHtml span(final String title,
                      final String className);
    }
}
