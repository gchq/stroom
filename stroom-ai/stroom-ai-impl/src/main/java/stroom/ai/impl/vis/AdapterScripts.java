/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.vis;

import stroom.dashboard.impl.script.ScriptContentProvider;
import stroom.script.shared.ScriptDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/// Serves this release's adapter for each adapter Script doc Stroom put in place (see
/// [ChartLibraries#adapterOf(ScriptDoc)]), whatever the doc holds. Each release's changes to the adapters then reach
/// every chart Stroom AI generated, with no chart generated again and no doc changed; and the nodes of a cluster
/// each serve their own, so nothing is done as Stroom starts.
@Singleton
public class AdapterScripts implements ScriptContentProvider {

    private final ChartLibraries chartLibraries;

    @Inject
    public AdapterScripts(final ChartLibraries chartLibraries) {
        this.chartLibraries = chartLibraries;
    }

    @Override
    public Optional<String> content(final ScriptDoc script) {
        return ChartLibraries.adapterOf(script).map(chartLibraries::adapterSource);
    }
}
