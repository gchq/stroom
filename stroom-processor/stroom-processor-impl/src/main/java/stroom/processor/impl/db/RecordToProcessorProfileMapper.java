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

package stroom.processor.impl.db;

import stroom.processor.shared.ProcessorProfile;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import org.jooq.JSON;
import org.jooq.Record;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorProfile.PROCESSOR_PROFILE;

class RecordToProcessorProfileMapper implements Function<Record, ProcessorProfile> {

    @Override
    public ProcessorProfile apply(final Record record) {
        final JSON json = record.get(PROCESSOR_PROFILE.PERIODS);
        final ProcessorProfilePeriods processorProfilePeriods = NullSafe
                .getOrElseGet(json,
                        JSON::data,
                        string -> JsonUtil.readValue(string, ProcessorProfilePeriods.class),
                        () -> new ProcessorProfilePeriods(null, null));

        return ProcessorProfile
                .builder()
                .id(record.get(PROCESSOR_PROFILE.ID))
                .version(record.get(PROCESSOR_PROFILE.VERSION))
                .createTimeMs(record.get(PROCESSOR_PROFILE.CREATE_TIME_MS))
                .createUser(record.get(PROCESSOR_PROFILE.CREATE_USER))
                .updateTimeMs(record.get(PROCESSOR_PROFILE.UPDATE_TIME_MS))
                .updateUser(record.get(PROCESSOR_PROFILE.UPDATE_USER))
                .name(record.get(PROCESSOR_PROFILE.NAME))
                .nodeGroupName(record.get(PROCESSOR_PROFILE.NODE_GROUP_NAME))
                .profilePeriods(processorProfilePeriods.getProfilePeriods())
                .timeZone(processorProfilePeriods.getTimeZone())
                .build();
    }
}
