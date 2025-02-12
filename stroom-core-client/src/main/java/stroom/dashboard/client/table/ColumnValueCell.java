package stroom.dashboard.client.table;

import stroom.cell.tickbox.client.TickBoxCell;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ColumnValueCell extends AbstractCell<String> {

    private static Template template;

    private final ColumnValueSelectionModel selectionModel;
    private final TickBoxCell tickBoxCell;

    public ColumnValueCell(final ColumnValueSelectionModel selectionModel) {
        this.selectionModel = selectionModel;
        if (template == null) {
            template = GWT.create(Template.class);
        }
        tickBoxCell = TickBoxCell.create(true, false);
    }

    @Override
    public void render(final Context context, final String item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final SafeHtml textHtml = template.text("columnValueCell-text",
                    SafeHtmlUtils.fromString(item));

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            tickBoxCell.render(context, selectionModel.getState(item), content);
            content.append(textHtml);

            sb.append(template.outer(content.toSafeHtml()));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml text(String textClass, SafeHtml text);

        @Template("<div class=\"columnValueCell\">{0}</div>")
        SafeHtml outer(SafeHtml content);
    }
}
