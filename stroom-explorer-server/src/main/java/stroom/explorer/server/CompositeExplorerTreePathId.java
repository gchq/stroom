/*
 * Copyright 2016 Crown Copyright
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

package stroom.explorer.server;

import stroom.util.shared.EqualsBuilder;

import java.io.Serializable;

public class CompositeExplorerTreePathId implements Serializable {
    private long ancestor;
    private long descendant;

    public long getAncestor() {
        return ancestor;
    }

    public void setAncestor(long ancestorId) {
        this.ancestor = ancestorId;
    }

    public long getDescendant() {
        return descendant;
    }

    public void setDescendant(long descendantId) {
        this.descendant = descendantId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof CompositeExplorerTreePathId)) {
            return false;
        }

        final CompositeExplorerTreePathId compositeExplorerTreePathId = (CompositeExplorerTreePathId) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(ancestor, compositeExplorerTreePathId.ancestor);
        builder.append(descendant, compositeExplorerTreePathId.descendant);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(getDescendant()) * 31 + Long.hashCode(getAncestor());
    }
}