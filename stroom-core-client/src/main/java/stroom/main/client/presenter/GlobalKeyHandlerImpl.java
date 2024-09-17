package stroom.main.client.presenter;

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.GlobalKeyHandler;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

@Singleton
public class GlobalKeyHandlerImpl implements GlobalKeyHandler {

    private TabData selectedTabData;

    @Inject
    GlobalKeyHandlerImpl(final EventBus eventBus) {
        // track the currently selected tab
        eventBus.addHandler(ContentTabSelectionChangeEvent.getType(), e -> {
            selectedTabData = e.getTabData();
//            if (selectedTabData != null) {
//                GWT.log("Selected tab - label: '" + tabData.getLabel()
//                        + "', type: " + tabData.getType()
//                        + ", class: " + tabData.getClass().getName());
//            }
        });
    }

    @Override
    public void onKeyDown(final KeyDownEvent event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        // If there is a Command associated with this key bind then this will execute that
        // else we pass the action down to the current tab to deal with
        final Action action = KeyBinding.test(nativeEvent);
        if (action != null && selectedTabData != null) {
//                GWT.log("Passing " + action + " to " + selectedTabData.getClass().getName());
            final boolean wasActionConsumed = selectedTabData.handleKeyAction(action);
            if (wasActionConsumed) {
                // Stop anyone else dealing with this key bind
                nativeEvent.stopPropagation();
                nativeEvent.preventDefault();
            }
        }
    }

    @Override
    public void onKeyUp(final KeyUpEvent event) {
        KeyBinding.test(event.getNativeEvent());
    }
}
