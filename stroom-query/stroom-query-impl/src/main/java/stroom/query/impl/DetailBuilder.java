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

package stroom.query.impl;

import java.util.function.Consumer;

public class DetailBuilder extends AbstractHtmlBuilder<DetailBuilder> {

    public DetailBuilder() {
        startElem("div");
    }

    public DetailBuilder title(final String title) {
        elem("b", b -> b.append(title));
        emptyElem("hr");
        return self();
    }

    public DetailBuilder description(final Consumer<DetailBuilder> consumer) {
        elem("p", "queryHelpDetail-description", consumer);
        return self();
    }

    public DetailBuilder table(final Consumer<DetailBuilder> consumer) {
        elem("table", consumer);
        return self();
    }

    public DetailBuilder appendKVRow(final String key, final String value) {
        elem("tr", tr -> {
            tr.elem("td", td -> td.elem("b", b -> b.append(key)));
            tr.elem("td", td -> td.append(value));
        });
        return self();
    }

    @Override
    public DetailBuilder self() {
        return this;
    }

    public String build() {
        endElem("div");
        return toString();
    }
}
