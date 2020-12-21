/*
 * Copyright 2016 Crown Copyright
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

package stroom.event.logging.api;

import stroom.util.shared.PageResponse;

import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventDetail.Builder;
import event.logging.EventLoggingService;
import event.logging.ResultPage;

import java.math.BigInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public interface StroomEventLoggingService extends EventLoggingService {

    Event createAction(final String typeId,
                       final String description);

    Event createAction(final String typeId,
                       final String description,
                       final Consumer<Builder<Void>> eventDetailBuilderConsumer);

    void log(final String typeId,
             final String description,
             final Consumer<Builder<Void>> eventDetailBuilderConsumer);

    <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
            final String eventTypeId,
            final String description,
            final T_EVENT_ACTION eventAction,
            final Function<T_EVENT_ACTION, T_RESULT> loggedWork);

    <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
            final String eventTypeId,
            final String description,
            final T_EVENT_ACTION eventAction,
            final Function<T_EVENT_ACTION, T_RESULT> loggedWork,
            final BiConsumer<Throwable, T_EVENT_ACTION> exceptionHandler);

    default ResultPage createResultPage(final stroom.util.shared.ResultPage<?> resultPage) {
        final ResultPage result;
        if (resultPage == null) {
            result = null;
        } else {
            final PageResponse pageResponse = resultPage.getPageResponse();
            if (resultPage.getPageResponse() != null) {
                result = ResultPage.builder()
                        .withFrom(BigInteger.valueOf(pageResponse.getOffset()))
                        .withPerPage(BigInteger.valueOf(pageResponse.getLength()))
                        .build();
            } else {
                result = null;
            }
        }
        return result;
    }
}
