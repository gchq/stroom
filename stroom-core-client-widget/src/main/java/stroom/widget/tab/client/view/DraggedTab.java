package stroom.widget.tab.client.view;

import stroom.widget.tab.client.presenter.TabData;

public class DraggedTab {

    private final TabData tabData;
    private int index;
    private final int tabWidth;

    public DraggedTab(final TabData tabData, final int index, final int tabWidth) {
        this.tabData = tabData;
        this.index = index;
        this.tabWidth = tabWidth;
    }


    public TabData getTabData() {
        return tabData;
    }

    public int getIndex() {
        return index;
    }

    public int getTabWidth() {
        return tabWidth;
    }

    public void setIndex(final int index) {
        this.index = index;
    }
}
