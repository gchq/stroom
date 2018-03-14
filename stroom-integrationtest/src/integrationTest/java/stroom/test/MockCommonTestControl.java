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

package stroom.test;

import stroom.entity.shared.Clearable;
import stroom.guice.StroomBeanStore;

import javax.inject.Inject;
import java.util.Set;

/**
 * Version of the test control used with the mocks.
 */
public class MockCommonTestControl implements CommonTestControl {
    private final StroomBeanStore beanStore;

    @Inject
    MockCommonTestControl(final StroomBeanStore beanStore) {
        this.beanStore = beanStore;
    }

    @Override
    public void setup() {
    }

    @Override
    public void teardown() {
        final Set<Clearable> set = beanStore.getBeansOfType(Clearable.class);
        set.forEach(Clearable::clear);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public int countEntity(final Class clazz) {
        return 0;
    }

    @Override
    public void deleteEntity(Class<?> clazz) {
    }

    public void setMockStreamStore(final Clearable mockStreamStore) {
    }

    public void setMockTranslationStreamTaskService(final Clearable mockTranslationStreamTaskService) {
    }

    public void setMockFeedService(final Clearable mockFeedService) {
    }

    @Override
    public void shutdown() {
        // NA
    }

    @Override
    public void createRequiredXMLSchemas() {
        // NA
    }
}
