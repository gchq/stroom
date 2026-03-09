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

package stroom.planb.impl.db.trace;

import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.NamesPathKey;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.TerminalPathKey;

import java.util.List;

public class PathKeyFactoryImpl implements PathKeyFactory {

    @Override
    public PathKey create(final List<Span> spans) {
        if (spans == null || spans.isEmpty()) {
            return TerminalPathKey.INSTANCE;
        } else if (spans.size() == 1) {
            return new NamePathKey(spans.getFirst().getName());
        }
        final List<String> names = spans.stream().map(Span::getName).toList();
        return new NamesPathKey(names);
    }
}
