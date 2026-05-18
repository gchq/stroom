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

package stroom.annotation.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;

/**
 * When there has been a change to an existing annotation tag (collection/status/label), i.e.
 * UPDATE/DELETE. Don't care about CREATE at the moment.
 */
public class AnnotationTagNameChangeEvent extends GwtEvent<AnnotationTagNameChangeEvent.Handler> {

    private static Type<AnnotationTagNameChangeEvent.Handler> TYPE;

    public static void fire(final HasHandlers source) {
        source.fireEvent(new AnnotationTagNameChangeEvent());
    }

    /**
     * Let the UI know the annotation has changed but not until we have shown the annotation
     * (hence deferred by timer).
     */
    public static void fireDeferred(final HasHandlers source) {
        new Timer() {
            @Override
            public void run() {
                source.fireEvent(new AnnotationTagNameChangeEvent());
            }
        }.schedule(1000);
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
        handler.onChange(this);
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onChange(AnnotationTagNameChangeEvent event);
    }
}
