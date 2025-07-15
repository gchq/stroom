package stroom.widget.tab.client.view;

import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.dom.client.DataTransfer.DropEffect;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.DragOverEvent;
import com.google.gwt.event.dom.client.DragStartEvent;
import com.google.gwt.event.dom.client.DropEvent;

import java.util.ArrayList;
import java.util.List;

public abstract class DraggableTabBar extends AbstractTabBar {

    private DraggedTab draggedTab;
    private int currentStartIndex = -1;

    public DraggableTabBar() {
        addDomHandler(this::onDragOver, DragOverEvent.getType());
        addDomHandler(this::onDrop, DropEvent.getType());
    }

    protected abstract AbstractTab createDraggableTab(TabData tabData);

    protected AbstractTab createTab(final TabData tabData) {
        final AbstractTab tab = createDraggableTab(tabData);

        tab.getElement().setDraggable(Element.DRAGGABLE_TRUE);
        tab.addDomHandler(event -> this.onDragStart(tab), DragStartEvent.getType());

        return tab;
    }

    private void onDragStart(final AbstractTab tab) {
        currentStartIndex = getTabs().indexOf(getVisibleTabs().get(0));

        final TabData tabData = getTabData(tab).get();
        final int dragIndex = getVisibleTabs().indexOf(tabData);
        final int dragTabWidth = tab.getOffsetWidth();
        draggedTab = new DraggedTab(tabData, dragIndex, dragTabWidth);

        keyboardSelectTab(tabData);
        fireTabSelection(tabData);
    }

    private void onDragOver(final DragOverEvent event) {
        event.getNativeEvent().getDataTransfer().setDropEffect(DropEffect.MOVE);

        if (draggedTab != null) {
            final int dragPos = event.getNativeEvent().getClientX() - getElement().getAbsoluteLeft();

            final int newDragIndex = getDragIndex(dragPos);

            if (newDragIndex != -1 && newDragIndex != draggedTab.getIndex()) {
                moveTab(draggedTab.getTabData(), currentStartIndex + newDragIndex, currentStartIndex);
                draggedTab.setIndex(newDragIndex);

                keyboardSelectTab(draggedTab.getTabData());
                fireTabSelection(draggedTab.getTabData());
            }
        }
    }

    private void onDrop(final DropEvent event) {
        event.preventDefault();

        currentStartIndex = -1;
        if (draggedTab != null) {
            draggedTab = null;
        }
    }

    private int getDragIndex(final int dragPos) {
        final ArrayList<TabData> visibleTabs = new ArrayList<>(getVisibleTabs());
        visibleTabs.remove(draggedTab.getTabData());

        int index = -1;

        for (int i = 0; i <= visibleTabs.size(); i++) {
            final List<TabData> tabList  = i == 0 ? new ArrayList<>() : visibleTabs.subList(0, i);
            final int tabListWidth = getTabListWidth(tabList);

            if ((dragPos >= tabListWidth && dragPos <= tabListWidth + draggedTab.getTabWidth()) ||
                (i == visibleTabs.size() && dragPos > tabListWidth)) {
                index = i;
                break;
            }
        }

        return index;
    }

    private int getTabListWidth(final List<TabData> tabList) {
        final int tabGap = getTabGap();

        int x = 0;

        for (final TabData tabData : tabList) {
            final AbstractTab tab = getTab(tabData);
            if (x > 0) {
                final Element separator = addSeparator();
                if (separator != null) {
                    separator.getStyle().setLeft(x, Unit.PX);
                    x += separator.getOffsetWidth();
                }
            }
            x += tab.getOffsetWidth() + tabGap;
        }

        return x;
    }

}
