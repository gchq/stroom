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

package stroom.headless;

import org.springframework.stereotype.Component;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsFacade;
import stroom.statistics.internal.InternalStatisticsFacadeFactory;

import java.util.List;
import java.util.function.Consumer;

@Component
public class HeadlessInternalStatisticsFacadeFactory implements InternalStatisticsFacadeFactory {
    @Override
    public InternalStatisticsFacade create() {
        return new HeadlessInternalStatisticsFacade();
    }

    private static class HeadlessInternalStatisticsFacade implements InternalStatisticsFacade {
        @Override
        public void putEvent(final InternalStatisticEvent internalStatisticEvent) {
        }

//        @Override
//        public void putEvent(final InternalStatisticEvent internalStatisticEvent, final Consumer<Throwable> exceptionHandler) {
//        }

        @Override
        public void putEvents(final List<InternalStatisticEvent> statisticEvents) {
        }

        @Override
        public BatchBuilder batchBuilder() {
            return new BatchBuilder(this);
        }

        @Override
        public void putEvents(final List<InternalStatisticEvent> statisticEvents, final Consumer<Throwable> exceptionHandler) {
        }
    }
}
