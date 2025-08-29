package stroom.dashboard.client.table;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ColumnValueCell extends AbstractCell<String> {

    private final ColumnValueSelectionModel selectionModel;
    private final TickBoxCell tickBoxCell;

    public ColumnValueCell(final ColumnValueSelectionModel selectionModel) {
        this.selectionModel = selectionModel;
        tickBoxCell = TickBoxCell.create(true, false);
    }

    @Override
    public void render(final Context context, final String item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final SafeHtml textHtml = Templates.div("columnValueCell-text",
                    SafeHtmlUtils.fromString(item));

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            tickBoxCell.render(context, selectionModel.getState(item), content);
            content.append(textHtml);

            sb.append(Templates.div("columnValueCell", content.toSafeHtml()));
        }
    }
}
