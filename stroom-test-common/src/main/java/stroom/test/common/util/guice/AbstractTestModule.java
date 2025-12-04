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

package stroom.test.common.util.guice;

import com.google.inject.AbstractModule;

public class AbstractTestModule extends AbstractModule {

    /**
     * Binds clazz to a Mockito mock instance.
     *
     * @return The mock instance.
     */
    public <T> T bindMock(final Class<T> clazz) {
        return GuiceTestUtil.bindMock(binder(), clazz);
    }
}
