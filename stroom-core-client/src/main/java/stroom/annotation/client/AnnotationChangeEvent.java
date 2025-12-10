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

import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;

public class AnnotationChangeEvent extends GwtEvent<AnnotationChangeEvent.Handler> {

    private static Type<AnnotationChangeEvent.Handler> TYPE;

    private final DocRef annotationRef;

    private AnnotationChangeEvent(final DocRef annotationRef) {
        this.annotationRef = annotationRef;
    }

    public static void fire(final HasHandlers source, final DocRef annotationRef) {
        source.fireEvent(new AnnotationChangeEvent(annotationRef));
    }

    /**
     * Let the UI know the annotation has changed but not until we have shown the annotation
     * (hence deferred by timer).
     */
    public static void fireDeferred(final HasHandlers source, final DocRef annotationRef) {
        new Timer() {
            @Override
            public void run() {
                source.fireEvent(new AnnotationChangeEvent(annotationRef));
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

    public DocRef getAnnotationRef() {
        return annotationRef;
    }

    public interface Handler extends EventHandler {

        void onChange(AnnotationChangeEvent event);
    }
}
