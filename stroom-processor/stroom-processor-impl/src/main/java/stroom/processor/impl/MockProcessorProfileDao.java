/*
 * Copyright 2025 Crown Copyright
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

import stroom.processor.shared.FindProcessorProfileRequest;
import stroom.processor.shared.ProcessorProfile;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

@Singleton
public class MockProcessorProfileDao implements ProcessorProfileDao, Clearable {

    private final MockIntCrud<ProcessorProfile> dao = new MockIntCrud<>(
            (processorProfile, integer) -> processorProfile.copy().id(integer).build(),
            ProcessorProfile::getId);

    @Override
    public ResultPage<ProcessorProfile> find(final FindProcessorProfileRequest request) {
        final String filter = request.getFilter();
        final List<ProcessorProfile> list = dao.getMap().values()
                .stream()
                .filter(processorProfile -> NullSafe.isBlankString(filter)
                                            || NullSafe.test(processorProfile.getName(),
                                                    name -> name.startsWith(filter)))
                .toList();
        return ResultPage.createCriterialBasedList(list, request);
    }

    @Override
    public List<String> getNames() {
        return dao.getMap().values()
                .stream()
                .map(ProcessorProfile::getName)
                .toList();
    }

    @Override
    public ProcessorProfile create(final ProcessorProfile processorProfile) {
        return dao.create(processorProfile);
    }

    @Override
    public ProcessorProfile fetchById(final int id) {
        return dao.fetch(id).orElse(null);
    }

    @Override
    public ProcessorProfile fetchByName(final String name) {
        return dao.getMap().values()
                .stream()
                .filter(processorProfile -> Objects.equals(processorProfile.getName(), name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public ProcessorProfile update(final ProcessorProfile processorProfile) {
        return dao.update(processorProfile);
    }

    @Override
    public void delete(final int id) {
        dao.delete(id);
    }

    @Override
    public void clear() {
        dao.clear();
    }
}
