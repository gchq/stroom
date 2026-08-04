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
import stroom.query.language.functions.Val;

import jakarta.inject.Inject;

public class StateFetcherImpl implements StateFetcher {

    private final StateProvider provider;

    @Inject
    public StateFetcherImpl(final StateProvider provider) {
        // A single provider, deliberately. This used to iterate a Set<StateProvider> returning the first non
        // null value, but a ValErr counted as a value, so one provider's failure could mask another provider's
        // data, see gh-5692. With a plain binding a second provider is a duplicate binding error at startup
        // rather than a silent precedence problem.
        this.provider = provider;
    }

    @Override
    public Val getState(final String map, final String key, final long effectiveTimeMs) {
        // The provider contract is that this is never null, see StateProvider.getState.
        return provider.getState(map, key, effectiveTimeMs);
    }
}
