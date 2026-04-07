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

package stroom.dashboard.client.flexlayout;

public class MutableTabConfig {

    private final String id;
    private boolean visible;
    private MutableTabLayoutConfig parent;

    public MutableTabConfig(final String id,
                            final boolean visible) {
        this.id = id;
        this.visible = visible;
    }

    public String getId() {
        return id;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(final boolean visible) {
        this.visible = visible;
    }

    public MutableTabLayoutConfig getParent() {
        return parent;
    }

    public void setParent(final MutableTabLayoutConfig parent) {
        this.parent = parent;
    }
}
