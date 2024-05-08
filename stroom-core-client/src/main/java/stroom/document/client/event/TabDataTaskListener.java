package stroom.document.client.event;

import stroom.task.client.TaskListener;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

public class TabDataTaskListener implements TaskListener, HasHandlers {

    private final EventBus eventBus;
    private final TabData tabData;

    public TabDataTaskListener(final EventBus eventBus,
                               final TabData tabData) {
        this.eventBus = eventBus;
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
        eventBus.fireEvent(gwtEvent);
    }
}
