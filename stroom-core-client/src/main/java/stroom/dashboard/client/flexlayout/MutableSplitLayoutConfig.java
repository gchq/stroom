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

import java.util.ArrayList;
import java.util.List;

public final class MutableSplitLayoutConfig extends MutableLayoutConfig {

    private final MutableSize preferredSize;
    private final int dimension;
    private final List<MutableLayoutConfig> children = new ArrayList<>();

    public MutableSplitLayoutConfig(final int dimension) {
        this(new MutableSize(), dimension);
    }

    public MutableSplitLayoutConfig(final MutableSize preferredSize,
                                    final int dimension) {
        this.preferredSize = preferredSize;
        this.dimension = dimension;
    }

    @Override
    public MutableSize getPreferredSize() {
        return preferredSize;
    }

    public int getDimension() {
        return dimension;
    }

    public List<MutableLayoutConfig> getChildren() {
        return children;
    }

    public MutableLayoutConfig get(final int index) {
        final MutableLayoutConfig child = children.get(index);
        if (child == null) {
            return null;
        }
        child.setParent(this);
        return child;
    }

    public void add(final MutableLayoutConfig child) {
        children.add(child);
        child.setParent(this);
    }

    public void add(final int index, final MutableLayoutConfig child) {
        if (index <= children.size()) {
            children.add(index, child);
        } else {
            children.add(child);
        }
        child.setParent(this);
    }

    public void remove(final MutableLayoutConfig child) {
        children.remove(child);
        child.setParent(null);
    }

    public int indexOf(final MutableLayoutConfig child) {
        return children.indexOf(child);
    }

    public int count() {
        return children.size();
    }
}
