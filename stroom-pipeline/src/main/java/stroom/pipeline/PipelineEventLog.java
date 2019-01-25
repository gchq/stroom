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
 *
 */

package stroom.pipeline;

import event.logging.Data;
import event.logging.Event;
import event.logging.ObjectOutcome;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;

import javax.inject.Inject;

public class PipelineEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineEventLog.class);

    private final StroomEventLoggingService eventLoggingService;

    @Inject
    PipelineEventLog(final StroomEventLoggingService eventLoggingService) {
        this.eventLoggingService = eventLoggingService;
    }

    public void stepStream(final String eventId,
                           final String feedName,
                           final String streamTypeName,
                           final DocRef pipelineRef,
                           final Throwable th) {
        try {
            if (eventId != null) {
                final Event event = eventLoggingService.createAction("Stepping", "Stepping Stream");
                final ObjectOutcome objectOutcome = new ObjectOutcome();
                event.getEventDetail().setView(objectOutcome);
                objectOutcome.getObjects().add(createStreamObject(eventId, feedName, streamTypeName, pipelineRef));
                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(th));
                eventLoggingService.log(event);
            }
        } catch (final Exception e) {
            LOGGER.error("Unable to step stream!", e);
        }
    }

    private event.logging.Object createStreamObject(final String eventId,
                                                    final String feedName,
                                                    final String streamTypeName,
                                                    final DocRef pipelineRef) {
        final event.logging.Object object = new event.logging.Object();
        object.setType("Stream");
        object.setId(eventId);
        if (feedName != null) {
            object.getData().add(EventLoggingUtil.createData("Feed", feedName));
        }
        if (streamTypeName != null) {
            object.getData().add(EventLoggingUtil.createData("StreamType", streamTypeName));
        }
        if (pipelineRef != null) {
            object.getData().add(convertDocRef("Pipeline", pipelineRef));
        }

        return object;
    }

    private Data convertDocRef(final String name, final DocRef docRef) {
        final Data data = new Data();
        data.setName(name);

        data.getData().add(EventLoggingUtil.createData("type", docRef.getType()));
        data.getData().add(EventLoggingUtil.createData("uuid", docRef.getUuid()));
        data.getData().add(EventLoggingUtil.createData("name", docRef.getName()));

        return data;
    }
}
