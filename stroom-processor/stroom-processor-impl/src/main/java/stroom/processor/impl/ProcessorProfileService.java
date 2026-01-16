/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorProfile;

import java.util.List;

public interface ProcessorProfileService {

    String ENTITY_TYPE = "PROCESSOR_PROFILE";
    DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, ENTITY_TYPE, ENTITY_TYPE);

    List<String> getNames();

    List<ProcessorProfile> getAll();

    ProcessorProfile create();

    ProcessorProfile getOrCreate(String name);

    ProcessorProfile update(ProcessorProfile indexVolumeGroup);

    ProcessorProfile get(String name);

    ProcessorProfile get(int id);

    void delete(int id);
}
