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

package stroom.meta.impl.db;

import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.meta.api.MetaSecurityFilter;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Optional;

public class MetaTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DataRetentionRulesProvider.class).toInstance(() -> null);
    }

    @Provides
    MetaSecurityFilter getMetaSecurityFilter() {
        return (permission, fields) -> Optional.empty();
    }
}
