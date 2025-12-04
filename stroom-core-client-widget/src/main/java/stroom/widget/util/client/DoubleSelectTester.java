/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.widget.util.client;

import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.Timer;

public class DoubleSelectTester implements HasHandlers {

    private static final int DOUBLE_SELECT_DELAY = DoubleClickTester.DOUBLE_CLICK_PERIOD;

    private final Timer doubleSelectTimer;
    private Object lastSelection;
    private boolean inDoubleSelectPeriod;

    private EventBus eventBus;

    public DoubleSelectTester() {
        // Create timer to deal with double clicks.
        doubleSelectTimer = new Timer() {
            @Override
            public void run() {
                clear();
            }
        };
    }

    /**
     * Test that the same object was clicked twice.
     */
    public boolean test(final Object selection) {
        if (inDoubleSelectPeriod && ((selection == null && lastSelection == null)
                || (selection != null && selection.equals(lastSelection)))) {
            clear();

            DoubleSelectEvent.fire(this);
            return true;

        } else {
            lastSelection = selection;
            inDoubleSelectPeriod = true;
            doubleSelectTimer.cancel();
            doubleSelectTimer.schedule(DOUBLE_SELECT_DELAY);
            return false;
        }
    }

    /**
     * Just test for a double click.
     */
    public boolean test() {
        if (inDoubleSelectPeriod) {
            clear();
            return true;

        } else {
            inDoubleSelectPeriod = true;
            doubleSelectTimer.cancel();
            doubleSelectTimer.schedule(DOUBLE_SELECT_DELAY);
            return false;
        }
    }

    private void clear() {
        lastSelection = null;
        inDoubleSelectPeriod = false;
    }

    public HandlerRegistration addDoubleSelectHandler(final DoubleSelectEvent.Handler handler) {
        if (eventBus == null) {
            eventBus = new SimpleEventBus();
        }

        return eventBus.addHandler(DoubleSelectEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        if (eventBus != null) {
            eventBus.fireEvent(event);
        }
    }
}
