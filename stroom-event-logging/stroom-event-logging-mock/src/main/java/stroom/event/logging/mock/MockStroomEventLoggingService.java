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

package stroom.event.logging.mock;

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.StroomEventLoggingService;

import event.logging.BaseObject;
import event.logging.Criteria;
import event.logging.Data;
import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventLoggerBuilder.TypeIdStep;
import event.logging.EventLoggingService;
import event.logging.Purpose;
import event.logging.impl.MockEventLoggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

public class MockStroomEventLoggingService implements EventLoggingService, StroomEventLoggingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockStroomEventLoggingService.class);

//    @Override
//    public Event createSkeletonEvent(final String typeId, final String description) {
//        return Event.builder()
//                .withEventDetail(EventDetail.builder()
//                        .withTypeId(typeId)
//                        .withDescription(description)
//                        .build())
//                .build();
//    }
//
//    @Override
//    public Event createSkeletonEvent(final String typeId,
//                                     final String description,
//                                     final Consumer<Builder<Void>> eventDetailBuilderConsumer) {
//        final EventDetail.Builder<Void> eventDetailBuilder = EventDetail.builder()
//                .withTypeId(typeId)
//                .withDescription(description);
//
//        if (eventDetailBuilderConsumer != null) {
//            eventDetailBuilderConsumer.accept(eventDetailBuilder);
//        }
//
//        return buildEvent()
//                .withEventDetail(eventDetailBuilder.build())
//                .build();
//    }

    @Override
    public void log(final Event event) {
        LOGGER.info("log called for event {}", event);
    }

    @Override
    public void log(final String typeId,
                    final String description,
                    final EventAction eventAction) {
        LOGGER.info("log called for typeId {}, description {}", typeId, description);
    }

    @Override
    public void log(final String typeId,
                    final String description,
                    final Purpose purpose,
                    final EventAction eventAction) {
        LOGGER.info("log called for typeId {}, description {}", typeId, description);
    }

    @Override
    public TypeIdStep loggedWorkBuilder() {
        return new MockEventLoggerBuilder<>(this);
    }

    @Override
    public void setValidate(final Boolean validate) {

    }

    @Override
    public boolean isValidate() {
        return false;
    }

    @Override
    public BaseObject convert(final Object object, final boolean useInfoProviders) {
        return null;
    }

    @Override
    public BaseObject convert(final Object object) {
        return null;
    }

    @Override
    public BaseObject convert(final Supplier<?> objectSupplier, final boolean useInfoProviders) {
        return null;
    }

    @Override
    public BaseObject convert(final Supplier<?> objectSupplier) {
        return null;
    }

    @Override
    public Criteria convertExpressionCriteria(final String type, final ExpressionCriteria expressionCriteria) {
        return null;
    }

    @Override
    public String describe(final Object object) {
        return null;
    }

    @Override
    public List<Data> getDataItems(final Object obj) {
        return null;
    }

}
