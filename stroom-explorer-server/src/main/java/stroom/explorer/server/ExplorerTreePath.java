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

import fri.util.database.jpa.tree.closuretable.ClosureTableTreeNode;
import fri.util.database.jpa.tree.closuretable.TreePath;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "explorerTreePath")
@IdClass(CompositeExplorerTreePathId.class)    // has a composite primary key
public class ExplorerTreePath implements TreePath {
    private ClosureTableTreeNode ancestor;
    private ClosureTableTreeNode descendant;
    private int depth;
    private int orderIndex;

    @Id
    @ManyToOne(targetEntity = ExplorerTreeNode.class)
    @JoinColumn(name = "ancestor", columnDefinition = "INT", nullable = false)    // the name of the database foreign key column
    @Override
    public ClosureTableTreeNode getAncestor() {
        return ancestor;
    }

    @Override
    public void setAncestor(ClosureTableTreeNode ancestor) {
        this.ancestor = ancestor;
    }

    @Id
    @ManyToOne(targetEntity = ExplorerTreeNode.class)
    @JoinColumn(name = "descendant", columnDefinition = "INT", nullable = false)    // the name of the database foreign key column
    @Override
    public ClosureTableTreeNode getDescendant() {
        return descendant;
    }

    @Override
    public void setDescendant(ClosureTableTreeNode descendant) {
        this.descendant = descendant;
    }

    @Column(name = "depth", columnDefinition = "INT", nullable = false)
    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public void setDepth(int depth) {
        this.depth = depth;
    }

    @Column(name = "orderIndex", columnDefinition = "INT", nullable = false)
    @Override
    public int getOrderIndex() {
        return orderIndex;
    }

    @Override
    public void setOrderIndex(int position) {
        this.orderIndex = position;
    }
}