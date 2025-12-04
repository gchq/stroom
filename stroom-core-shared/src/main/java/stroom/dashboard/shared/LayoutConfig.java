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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SplitLayoutConfig.class, name = "splitLayout"),
        @JsonSubTypes.Type(value = TabLayoutConfig.class, name = "tabLayout")
})
public abstract sealed class LayoutConfig permits SplitLayoutConfig, TabLayoutConfig {

    @JsonIgnore
    private transient SplitLayoutConfig parent;

    public abstract Size getPreferredSize();

    @JsonIgnore
    public SplitLayoutConfig getParent() {
        return parent;
    }

    @JsonIgnore
    public void setParent(final SplitLayoutConfig parent) {
        this.parent = parent;
    }

    public abstract AbstractBuilder<?, ?> copy();

    public abstract static class AbstractBuilder<T extends LayoutConfig, B extends AbstractBuilder<T, ?>> {

        protected Size preferredSize;

        public B preferredSize(final Size preferredSize) {
            this.preferredSize = preferredSize;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }
}
