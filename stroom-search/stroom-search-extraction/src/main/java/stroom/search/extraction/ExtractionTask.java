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

package stroom.search.extraction;

import stroom.alert.api.AlertDefinition;
import stroom.docref.DocRef;
import stroom.search.coprocessor.Receiver;

import java.util.List;
import java.util.Map;

class ExtractionTask {
    private final long streamId;
    private final long[] eventIds;
    private final DocRef pipelineRef;
    private final Receiver receiver;
    private final List<AlertDefinition> alertDefinitions;
    private final Map<String, String> paramMapForAlerting;

    ExtractionTask(final long streamId,
                   final long[] eventIds,
                   final DocRef pipelineRef,
                   final Receiver receiver) {
        this.streamId = streamId;
        this.eventIds = eventIds;
        this.pipelineRef = pipelineRef;
        this.receiver = receiver;
        this.alertDefinitions = null;
        this.paramMapForAlerting = null;
    }

    ExtractionTask(final long streamId,
                   final long[] eventIds,
                   final DocRef pipelineRef,
                   final Receiver receiver,
                   final List<AlertDefinition> alertTableSettings,
                   final Map<String, String> paramMap) {
        this.streamId = streamId;
        this.eventIds = eventIds;
        this.pipelineRef = pipelineRef;
        this.receiver = receiver;
        this.alertDefinitions = alertTableSettings;
        this.paramMapForAlerting = paramMap;
    }

    long getStreamId() {
        return streamId;
    }

    long[] getEventIds() {
        return eventIds;
    }

    DocRef getPipelineRef() {
        return pipelineRef;
    }

    Receiver getReceiver() {
        return receiver;
    }

    boolean isAlerting(){
        return alertDefinitions != null;
    }

    final List<AlertDefinition> getAlertTableSettings(){
        return alertDefinitions;
    }

    final Map<String, String> getParamMapForAlerting(){
        return paramMapForAlerting;
    }

}