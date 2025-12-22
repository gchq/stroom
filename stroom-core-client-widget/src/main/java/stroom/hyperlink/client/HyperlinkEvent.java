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

package stroom.hyperlink.client;

import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class HyperlinkEvent extends GwtEvent<HyperlinkEvent.Handler> {

    private static Type<Handler> TYPE;

    private final Hyperlink hyperlink;
    private final TaskMonitorFactory taskMonitorFactory;
    private final Object context;

    private HyperlinkEvent(final Hyperlink hyperlink,
                           final TaskMonitorFactory taskMonitorFactory,
                           final Object context) {
        this.hyperlink = hyperlink;
        this.taskMonitorFactory = taskMonitorFactory;
        this.context = context;
    }

    public static void fire(final HasHandlers handlers,
                            final Hyperlink hyperlink,
                            final TaskMonitorFactory taskMonitorFactory) {
        handlers.fireEvent(new HyperlinkEvent(hyperlink, taskMonitorFactory, null));
    }

    public static void fire(final HasHandlers handlers,
                            final Hyperlink hyperlink,
                            final TaskMonitorFactory taskMonitorFactory,
                            final Object context) {
        handlers.fireEvent(new HyperlinkEvent(hyperlink, taskMonitorFactory, context));
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
        handler.onLink(this);
    }

    public Hyperlink getHyperlink() {
        return hyperlink;
    }

    public TaskMonitorFactory getTaskMonitorFactory() {
        return taskMonitorFactory;
    }

    public Object getContext() {
        return context;
    }

    public interface Handler extends EventHandler {

        void onLink(HyperlinkEvent event);
    }
}
