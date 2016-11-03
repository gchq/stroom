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

package stroom.explorer.shared;

import stroom.entity.shared.DocRef;
import stroom.entity.shared.NamedEntity;
import stroom.util.shared.SharedObject;

import java.util.Set;

/**
 * This is a light-weight class used to represent an entity object and provide
 * some additional properties for constructing a tree.
 */
public class EntityData implements ExplorerData, SharedObject {
    private static final long serialVersionUID = -5216736591679930246L;

    private int depth;
    private String iconUrl;
    private DocRef docRef;
    private NodeState nodeState;
    private Set<String> tags;

    public EntityData() {
        // Default constructor necessary for GWT serialisation.
    }

    private EntityData(final String iconUrl, final DocRef docRef, final NodeState nodeState) {
        this.iconUrl = iconUrl;
        this.docRef = docRef;
        this.nodeState = nodeState;
    }

    public static EntityData create(final String iconUrl, final NamedEntity entity) {
        if (entity == null) {
            return null;
        }
        return create(iconUrl, entity, NodeState.LEAF);
    }

    public static EntityData create(final String iconUrl, final NamedEntity entity, final NodeState nodeState) {
        if (entity == null) {
            return null;
        }
        return new EntityData(iconUrl, DocRef.create(entity), nodeState);
    }

    public static EntityData create(final String iconUrl, final DocRef docRef, final NodeState nodeState) {
        if (docRef == null) {
            return null;
        }
        return new EntityData(iconUrl, docRef, nodeState);
    }

    public static EntityData create(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        return create(null, docRef, NodeState.LEAF);
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public void setDepth(int depth) {
        this.depth = depth;
    }

    @Override
    public String getIconUrl() {
        return iconUrl;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    @Override
    public String getType() {
        return docRef.getType();
    }

    @Override
    public NodeState getNodeState() {
        return nodeState;
    }

    @Override
    public void setNodeState(final NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public String getDisplayValue() {
        return docRef.getDisplayValue();
    }

    @Override
    public Set<String> getTags() {
        return tags;
    }

    public void setTags(final Set<String> tags) {
        this.tags = tags;
    }

    @Override
    public int hashCode() {
        return docRef.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof EntityData)) {
            return false;
        }

        final EntityData item = (EntityData) obj;
        return docRef.equals(item.docRef);
    }

    @Override
    public String toString() {
        return docRef.getDisplayValue();
    }
}
