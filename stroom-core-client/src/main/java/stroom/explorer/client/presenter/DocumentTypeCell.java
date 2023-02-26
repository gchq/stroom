package stroom.explorer.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.explorer.shared.DocumentType;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class DocumentTypeCell extends AbstractCell<DocumentType> {

    private static Template template;

    private final DocumentTypeSelectionModel selectionModel;
    private final TickBoxCell tickBoxCell;

    public DocumentTypeCell(final DocumentTypeSelectionModel selectionModel) {
//        super("click");
        this.selectionModel = selectionModel;
        if (template == null) {
            template = GWT.create(Template.class);
        }
        tickBoxCell = TickBoxCell.create(true, false);
    }

//    @Override
//    public void onBrowserEvent(final Context context,
//                               final Element parent,
//                               final DocumentType value,
//                               final NativeEvent event,
//                               final ValueUpdater<DocumentType> valueUpdater) {
//        if (value != null) {
//            super.onBrowserEvent(context, parent, value, event, valueUpdater);
//            final String type = event.getType();
//
//            final Element target = event.getEventTarget().cast();
//            if ("div".equalsIgnoreCase(target.getTagName())
//                    && "click".equals(type)
//                    && MouseUtil.isPrimary(event)) {
//
//                if (TickBoxAppearance.isTickBox(target)) {
//                    selectionModel.toggle(value);
//                }
//            }
//        }
//    }

    @Override
    public void render(final Context context, final DocumentType item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final SafeHtml iconHtml = template.icon("explorerCell-icon " + item.getIconClassName());
            final SafeHtml textHtml = template.text("explorerCell-text",
                    SafeHtmlUtils.fromString(item.getType()));

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            tickBoxCell.render(context, selectionModel.getState(item), content);
            content.append(iconHtml);
            content.append(textHtml);

            sb.append(template.outer(content.toSafeHtml()));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\"></div>")
        SafeHtml icon(String iconClass);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml text(String textClass, SafeHtml text);

        @Template("<div class=\"explorerCell\">{0}</div>")
        SafeHtml outer(SafeHtml content);
    }
}
