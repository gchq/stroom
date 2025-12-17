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

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;

import event.logging.Data;
import event.logging.OtherObject;
import event.logging.ViewEventAction;
import event.logging.util.EventLoggingUtil;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

                eventLoggingService.log(
                        "Stepping",
                        "Stepping Stream",
                        ViewEventAction.builder()
                                .withObjects(createStreamObject(eventId, feedName, streamTypeName, pipelineRef))
                                .withOutcome(EventLoggingUtil.createOutcome(th))
                                .build());
            }
        } catch (final Exception e) {
            LOGGER.error("Unable to step stream!", e);
        }
    }

    private OtherObject createStreamObject(final String eventId,
                                           final String feedName,
                                           final String streamTypeName,
                                           final DocRef pipelineRef) {
        final OtherObject.Builder<Void> objectBuilder = OtherObject.builder()
                .withType("Stream")
                .withId(eventId);

        if (feedName != null) {
            objectBuilder.addData(EventLoggingUtil.createData("Feed", feedName));
        }
        if (streamTypeName != null) {
            objectBuilder.addData(EventLoggingUtil.createData("StreamType", streamTypeName));
        }
        if (pipelineRef != null) {
            objectBuilder.addData(convertDocRef("Pipeline", pipelineRef));
        }

        return objectBuilder.build();
    }

    private Data convertDocRef(final String name, final DocRef docRef) {
        return Data.builder()
                .withName(name)
                .addData(Data.builder()
                        .withName("type")
                        .withValue(docRef.getType())
                        .build())
                .addData(Data.builder()
                        .withName("uuid")
                        .withValue(docRef.getUuid())
                        .build())
                .addData(Data.builder()
                        .withName("name")
                        .withValue(docRef.getName())
                        .build())
                .build();
    }
}
