package stroom.content.client.presenter;

import stroom.task.client.TaskListener;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class TabTaskListener implements TaskListener, HasHandlers {

    private final HasHandlers hasHandlers;
    private final TabData tabData;

    public TabTaskListener(final HasHandlers hasHandlers,
                           final TabData tabData) {
        this.hasHandlers = hasHandlers;
        this.tabData = tabData;
    }

    @Override
    public void incrementTaskCount() {
        TabDataStartTaskEvent.fire(this, tabData);
    }

    @Override
    public void decrementTaskCount() {
        TabDataEndTaskEvent.fire(this, tabData);
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        hasHandlers.fireEvent(gwtEvent);
    }
}
