package stroom.cell.info.client;

import stroom.data.grid.client.EventCell;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Shows a '...' hover icon to the right of the cell text value that when clicked
 * will show a menu of actions to perform.
 *
 * @param <T> Typically the type of the row so that the menu has access to all properties
 *            of that row.
 */
public class HoverActionMenuCell<T> extends AbstractCell<T> implements EventCell {

    private static final String ICON_CLASS_NAME = "svgIcon";
    private static final String HOVER_ACTION_MENU_CLASS_NAME = "hoverMenuAction";
    private static final SvgImage ICON_IMAGE = SvgImage.ELLIPSES_HORIZONTAL;

    private final Function<T, String> cellTextExtractor;
    private final Function<T, List<Item>> menuCreator;
    private final HasHandlers hasHandlers;

    /**
     * @param cellTextExtractor Function to supply the text value of the cell
     * @param menuCreator       A function to create a list of menu items to show when the hover
     *                          icon is clicked. If the list contains only one item and that item
     *                          is an instance of {@link stroom.widget.menu.client.presenter.MenuItem}
     *                          and has a command, then clicking the hover icon will fire the command
     *                          without showing the menu.
     */
    public HoverActionMenuCell(final Function<T, String> cellTextExtractor,
                               final Function<T, List<Item>> menuCreator,
                               final HasHandlers hasHandlers) {
        super(BrowserEvents.MOUSEDOWN);

        this.menuCreator = menuCreator;
        this.cellTextExtractor = cellTextExtractor;
        this.hasHandlers = hasHandlers;
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (BrowserEvents.MOUSEDOWN.equals(nativeEvent.getType())
            && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, HOVER_ACTION_MENU_CLASS_NAME, 5);
        }
        return false;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final T value,
                               final NativeEvent event,
                               final ValueUpdater<T> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if (BrowserEvents.MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
            onEnterKeyDown(context, parent, value, event, valueUpdater);
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final T value,
                                  final NativeEvent event,
                                  final ValueUpdater<T> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        if (ElementUtil.hasClassName(element, HOVER_ACTION_MENU_CLASS_NAME, 5)) {
            final List<Item> items = NullSafe.get(menuCreator, mc -> mc.apply(value));
            if (NullSafe.hasItems(items)) {
                if (items.size() == 1
                    && items.get(0) instanceof MenuItem
                    && ((MenuItem) items.get(0)).getCommand() != null) {
                    // Single item with a command so no menu popup needed
                    ((MenuItem) items.get(0)).getCommand().execute();
                } else {
                    // Build the menu popup
                    showActionMenu(value, event);
                }
            }
        }
        if (valueUpdater != null) {
            valueUpdater.update(value);
        }
    }

    private void showActionMenu(final T value, final NativeEvent event) {
        Objects.requireNonNull(menuCreator);
        final List<Item> items = menuCreator.apply(value);
        final PopupPosition popupPosition = new PopupPosition(
                event.getClientX() + 10,
                event.getClientY());
        ShowMenuEvent
                .builder()
                .items(items)
                .popupPosition(popupPosition)
                .fire(hasHandlers);
    }

    @Override
    public void render(final Context context, final T value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            String cellText = null;
            if (cellTextExtractor != null) {
                cellText = cellTextExtractor.apply(value);
            }

            final List<Item> menuItems = NullSafe.get(menuCreator, mc -> mc.apply(value));
            if (NullSafe.hasItems(menuItems)) {
                final SafeHtml textSafeHtml = Templates
                        .div("commandLinkText", SafeHtmlUtils.fromString(cellText));

                sb.appendHtmlConstant("<div class=\"commandLinkContainer\">");
                sb.append(textSafeHtml);

                final SafeHtml icon = SvgImageUtil.toSafeHtml(
                        ICON_IMAGE,
                        ICON_CLASS_NAME,
                        HOVER_ACTION_MENU_CLASS_NAME);

                if (menuItems.size() == 1 &&
                    menuItems.get(0) instanceof final MenuItem menuItem &&
                    menuItem.getCommand() != null) {
                    // Single item with a command so no menu popup needed
                    final SafeHtml tooltip = NullSafe.getOrElse(menuItem, MenuItem::getTooltip, menuItem.getText());
                    sb.append(Templates.divWithTitle(tooltip.asString(), icon));
                } else {
                    // Build the menu popup
                    sb.append(Templates.divWithTitle("Actions...", icon));
                }

                sb.appendHtmlConstant("</div>");
            } else {
                // No menu items so just show the text
                if (NullSafe.isBlankString(cellText)) {
                    sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
                } else {
                    sb.append(SafeHtmlUtils.fromString(cellText));
                }
            }
        }
    }
}
