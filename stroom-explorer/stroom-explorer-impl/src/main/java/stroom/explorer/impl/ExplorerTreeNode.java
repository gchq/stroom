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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.NullSafe;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ExplorerTreeNode implements HasIntegerId {

    private Integer id;
    private String type;
    private String uuid;
    private String name;
    private Set<String> tags;

    public ExplorerTreeNode() {
    }

    public ExplorerTreeNode(final Integer id,
                            final String type,
                            final String uuid,
                            final String name,
                            final Set<String> tags) {
        this.id = id;
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.tags = tags;
    }

    public static ExplorerTreeNode create(final DocRef docRef) {
        return create(docRef, null);
    }

    public static ExplorerTreeNode create(final DocRef docRef, final Set<String> tags) {
        if (docRef == null) {
            return null;
        }

        final ExplorerTreeNode explorerTreeNode = new ExplorerTreeNode();
        explorerTreeNode.setType(docRef.getType());
        explorerTreeNode.setUuid(docRef.getUuid());
        explorerTreeNode.setName(docRef.getName());
        explorerTreeNode.setTags(NullSafe.hasItems(tags)
                ? new HashSet<>(tags)
                : null);
        return explorerTreeNode;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(final Set<String> tags) {
        this.tags = tags;
    }

    public DocRef getDocRef() {
        return new DocRef(type, uuid, name);
    }

    @Override
    public ExplorerTreeNode clone() {
        final ExplorerTreeNode clone = new ExplorerTreeNode();
        clone.type = type;
        clone.uuid = uuid;
        clone.name = name;
        clone.tags = tags;
        return clone;
    }

    public ExplorerNode.Builder buildExplorerNode() {
        return ExplorerNode.builder()
                .type(type)
                .uuid(uuid)
                .name(name)
                .tags(tags);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExplorerTreeNode that = (ExplorerTreeNode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ExplorerTreeNode{" +
               "id=" + id +
               ", type='" + type + '\'' +
               ", uuid='" + uuid + '\'' +
               ", name='" + name + '\'' +
               ", tags='" + tags + '\'' +
               '}';
    }
}
