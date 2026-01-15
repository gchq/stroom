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

package stroom.task.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

/**
 * Opens the Server Tasks tab
 */
public class OpenTaskManagerEvent extends GwtEvent<OpenTaskManagerEvent.Handler> {

    private static Type<Handler> TYPE;
    private final String nodeName;
    private final String taskName;
    private final String userName;

    private OpenTaskManagerEvent(final String nodeName, final String taskName, final String userName) {
        // Private constructor.
        this.nodeName = nodeName;
        this.taskName = taskName;
        this.userName = userName;
    }

    public static void fire(final HasHandlers handlers,
                            final String taskName) {
        handlers.fireEvent(new OpenTaskManagerEvent(null, taskName, null));
    }

    public static void fire(final HasHandlers handlers,
                            final String nodeName,
                            final String taskName) {
        handlers.fireEvent(new OpenTaskManagerEvent(nodeName, taskName, null));
    }

    public static void fire(final HasHandlers handlers,
                            final String nodeName,
                            final String taskName,
                            final String userName) {
        handlers.fireEvent(new OpenTaskManagerEvent(nodeName, taskName, userName));
    }

    public static void fire(final HasHandlers handlers) {
        handlers.fireEvent(new OpenTaskManagerEvent(null, null, null));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onOpen(this);
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getUserName() {
        return userName;
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onOpen(OpenTaskManagerEvent event);
    }
}
