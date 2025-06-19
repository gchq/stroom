package stroom.cell.info.client;

import stroom.data.grid.client.EventCell;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Command;
import com.google.gwt.view.client.CellPreviewEvent;

import static com.google.gwt.dom.client.BrowserEvents.MOUSEDOWN;

public class CommandLinkCell extends AbstractCell<CommandLink> implements EventCell {

    private static final String ICON_CLASS_NAME = "svgIcon";
    private static final String OPEN_CLASS_NAME = "commandLinkOpen";
//    private static final String OPEN_CLASS_NAME = "docRefLinkOpen";

    private static Template template;

    public CommandLinkCell() {
        super(MOUSEDOWN);

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final CommandLink value,
                               final NativeEvent event, final ValueUpdater<CommandLink> valueUpdater) {
//        super.onBrowserEvent(context, parent, value, event, valueUpdater);
//
//        if (CLICK.equals(event.getType()) && valueUpdater != null && value.getCommand() != null) {
//            valueUpdater.update(value);
//        }

        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if (MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
            onEnterKeyDown(context, parent, value, event, valueUpdater);
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final CommandLink value,
                                  final NativeEvent event,
                                  final ValueUpdater<CommandLink> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        if (ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5)) {
            final Command command = NullSafe.get(value, CommandLink::getCommand);
            if (command != null) {
                command.execute();
            }
        }
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5);
        }
        return false;
    }

    @Override
    public void render(final Context context, final CommandLink value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else if (value.hasCommand()) {
            final String text = value.getText();

            final SafeHtml textSafeHtml = template
                    .div("commandLinkText", SafeHtmlUtils.fromString(text));

            sb.appendHtmlConstant("<div class=\"commandLinkContainer\">");
            sb.append(textSafeHtml);

            final SafeHtml open = SvgImageUtil.toSafeHtml(SvgImage.OPEN, ICON_CLASS_NAME, OPEN_CLASS_NAME);
            sb.append(template.divWithToolTip(
                    value.getTooltip(),
                    open));

            sb.appendHtmlConstant("</div>");

        } else {
            sb.append(template.text(value.getText()));
        }
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

//        @Template("<div class=\"CommandLinkCell\" title=\"{1}\">{0}</div>")
//        SafeHtml link(String text, String tooltip);

        @Template("{0}")
        SafeHtml text(String text);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String cssClass, SafeHtml content);

        @Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithToolTip(String title, SafeHtml content);
    }
}
