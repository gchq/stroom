/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline.stepping.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import stroom.explorer.shared.SharedDocRef;

public class BeginPipelineSteppingEvent extends GwtEvent<BeginPipelineSteppingEvent.Handler> {
    private static Type<Handler> TYPE;
    private final Long streamId;
    private final Long eventId;
    private final Long childStreamId;
    private final String childStreamType;
    private final SharedDocRef pipelineRef;

    private BeginPipelineSteppingEvent(final Long streamId, final Long eventId, final Long childStreamId,
                                       final String childStreamType, final SharedDocRef pipelineRef) {
        this.streamId = streamId;
        this.eventId = eventId;
        this.childStreamId = childStreamId;
        this.childStreamType = childStreamType;
        this.pipelineRef = pipelineRef;
    }

    public static void fire(final HasHandlers source, final Long streamId, final Long eventId, final Long childStreamId,
                            final String childStreamType, final SharedDocRef pipelineRef) {
        source.fireEvent(new BeginPipelineSteppingEvent(streamId, eventId, childStreamId, childStreamType, pipelineRef));
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
        handler.onBegin(this);
    }

    public Long getStreamId() {
        return streamId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getChildStreamId() {
        return childStreamId;
    }

    public String getChildStreamType() {
        return childStreamType;
    }

    public SharedDocRef getPipelineRef() {
        return pipelineRef;
    }

    public interface Handler extends EventHandler {
        void onBegin(BeginPipelineSteppingEvent event);
    }
}
