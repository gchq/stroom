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

package stroom.processor;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.HasIntCrud;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.Processor;
import stroom.util.logging.LambdaLogger;

import java.util.Optional;

public interface StreamProcessorService extends HasIntCrud<Processor> {

    Optional<Processor> fetchInsecure(final int id);

    Optional<Processor> fetchByUuid(final String uuid);

    default Processor fetchByUuidOrThrow(final String uuid) {
        return fetchByUuid(uuid)
                .orElseThrow(() -> new RuntimeException(
                        LambdaLogger.buildMessage("Could not find processor with UUID {}", uuid)));
    }

    BaseResultList<Processor> find(FindStreamProcessorCriteria criteria);

}
