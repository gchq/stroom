/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

/// Event fired when the selected time changes on the floor map timeline.
public class TimeChangeEvent extends GwtEvent<TimeChangeEvent.TimeChangeHandler> {

    private static Type<TimeChangeHandler> TYPE;
    private final long time;

    private TimeChangeEvent(final long time) {
        this.time = time;
    }

    /// Fires a time change event.
    ///
    /// @param handlers The handler source.
    /// @param time The new time value in milliseconds.
    public static void fire(final HasHandlers handlers, final long time) {
        handlers.fireEvent(new TimeChangeEvent(time));
    }

    public static Type<TimeChangeHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<TimeChangeHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final TimeChangeHandler handler) {
        handler.onTimeChange(this);
    }

    /// @return The new time value in milliseconds.
    public long getTime() {
        return time;
    }

    // --------------------------------------------------------------------------------

    /// Handler interface for [TimeChangeEvent].
    public interface TimeChangeHandler extends EventHandler {

        void onTimeChange(TimeChangeEvent event);
    }
}
