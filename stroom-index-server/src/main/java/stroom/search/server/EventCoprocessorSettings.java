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

package stroom.search.server;

import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.api.v2.DocRef;

public class EventCoprocessorSettings implements CoprocessorSettings {
    private static final long serialVersionUID = -4916050910828000494L;

    private EventRef minEvent;
    private EventRef maxEvent;
    private long maxStreams;
    private long maxEvents;
    private long maxEventsPerStream;

    public EventCoprocessorSettings() {
    }

    public EventCoprocessorSettings(final EventRef minEvent, final EventRef maxEvent, final long maxStreams,
                                    final long maxEvents, final long maxEventsPerStream) {
        this.minEvent = minEvent;
        this.maxEvent = maxEvent;
        this.maxStreams = maxStreams;
        this.maxEvents = maxEvents;
        this.maxEventsPerStream = maxEventsPerStream;
    }

    public EventRef getMinEvent() {
        return minEvent;
    }

    public EventRef getMaxEvent() {
        return maxEvent;
    }

    public long getMaxStreams() {
        return maxStreams;
    }

    public long getMaxEvents() {
        return maxEvents;
    }

    public long getMaxEventsPerStream() {
        return maxEventsPerStream;
    }

    @Override
    public boolean extractValues() {
        return false;
    }

    @Override
    public DocRef getExtractionPipeline() {
        return null;
    }
}
