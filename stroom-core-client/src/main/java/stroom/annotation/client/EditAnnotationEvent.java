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

public class EditAnnotationEvent extends GwtEvent<EditAnnotationEvent.Handler> {

    private static Type<EditAnnotationEvent.Handler> TYPE;

    private final long annotationId;

    private EditAnnotationEvent(final long annotationId) {
        this.annotationId = annotationId;
    }

    public static void fire(final HasHandlers source, final long annotationId) {
        source.fireEvent(new EditAnnotationEvent(annotationId));
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
        handler.onEdit(this);
    }

    public long getAnnotationId() {
        return annotationId;
    }

    public interface Handler extends EventHandler {

        void onEdit(EditAnnotationEvent event);
    }
}
