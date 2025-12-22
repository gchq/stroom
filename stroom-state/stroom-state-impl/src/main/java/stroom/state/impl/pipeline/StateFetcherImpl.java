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

package stroom.state.impl.pipeline;

import stroom.query.language.functions.StateFetcher;
import stroom.query.language.functions.StateProvider;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;

import jakarta.inject.Inject;

import java.util.Set;

public class StateFetcherImpl implements StateFetcher {

    private final Set<StateProvider> providers;

    @Inject
    public StateFetcherImpl(final Set<StateProvider> providers) {
        this.providers = providers;
    }

    @Override
    public Val getState(final String map, final String key, final long effectiveTimeMs) {
        for (final StateProvider provider : providers) {
            final Val val = provider.getState(map, key, effectiveTimeMs);
            if (val != null && !Type.NULL.equals(val.type())) {
                return val;
            }
        }
        return ValNull.INSTANCE;
    }
}
