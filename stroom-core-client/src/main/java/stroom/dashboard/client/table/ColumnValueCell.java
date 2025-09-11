package stroom.dashboard.client.table;

import stroom.query.api.ColumnValueSelection;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class ColumnValueCell extends AbstractCell<String> {

    private final ColumnValueSelection.Builder columnValueSelection;

    public ColumnValueCell(final ColumnValueSelection.Builder columnValueSelection) {
        this.columnValueSelection = columnValueSelection;
    }

    @Override
    public void render(final Context context, final String item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final HtmlBuilder hb = new HtmlBuilder(sb);
            final ColumnValueSelection selection = columnValueSelection.build();

            final boolean selected;
            if (selection.getValues().contains(item)) {
                selected = !selection.isInvert();
            } else {
                selected = selection.isInvert();
            }

            hb.div(outer -> {

                final SafeHtml safeHtml;
                if (selected) {
                    safeHtml = SvgImageUtil.toSafeHtml(
                            "Ticked",
                            SvgImage.TICK,
                            "tickBox",
                            "tickBox-noBorder",
                            "tickBox-tick");
                    outer.append(safeHtml);

                } else {
                    outer.div(d -> {
                            },
                            Attribute.title("Not Ticked"),
                            Attribute.className("tickBox tickBox-noBorder tickBox-untick"));
                }

                outer.div(item, Attribute.className("columnValueCell-text"));
            }, Attribute.className("columnValueCell"));
        }
    }
}
