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

package stroom.explorer;

import fri.util.database.jpa.tree.closuretable.ClosureTableTreeNode;
import stroom.query.api.v2.DocRef;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "explorerTreeNode", uniqueConstraints = @UniqueConstraint(columnNames = {"type", "uuid"}))
public class ExplorerTreeNode implements ClosureTableTreeNode {
    private Long id;
    private String type;
    private String uuid;
    private String name;
    private String tags;

    ExplorerTreeNode() {
    }

    ExplorerTreeNode(final String type, final String uuid, final String name, final String tags) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.tags = tags;
    }

    static ExplorerTreeNode create(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }

        final ExplorerTreeNode explorerTreeNode = new ExplorerTreeNode();
        explorerTreeNode.setType(docRef.getType());
        explorerTreeNode.setUuid(docRef.getUuid());
        explorerTreeNode.setName(docRef.getName());
        return explorerTreeNode;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "INT")
    @XmlTransient
    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    @Column(name = "type", nullable = false)
    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    @Column(name = "uuid", nullable = false)
    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Column(name = "tags")
    public String getTags() {
        return tags;
    }

    public void setTags(final String tags) {
        this.tags = tags;
    }

    @Transient
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
}