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

import java.util.List;
import java.util.Set;

public class UnmodifiableTreeModel {

    private final TreeModel treeModel;

    private UnmodifiableTreeModel(final TreeModel treeModel) {
        this.treeModel = treeModel;
    }

    public static UnmodifiableTreeModel wrap(final TreeModel treeModel) {
        return new UnmodifiableTreeModel(treeModel);
    }

    public long getId() {
        return treeModel.getId();
    }

    public long getCreationTime() {
        return treeModel.getCreationTime();
    }

    public Set<String> getAllParents() {
        return treeModel.getAllParents();
    }

    public ExplorerNode getParent(final String childKey) {
        return treeModel.getParent(childKey);
    }

    public ExplorerNode getNode(final String nodeKey) {
        return treeModel.getNode(nodeKey);
    }

    public ExplorerNode getNode(final DocRef docRef) {
        return treeModel.getNode(docRef);
    }

    public ExplorerNode getNode(final ExplorerNode node) {
        return treeModel.getNode(node);
    }

    public Set<String> getAllTags() {
        return treeModel.getAllTags();
    }

    public void addRoot(final ExplorerNode rootNode) {
        treeModel.addRoot(rootNode);
    }

    public boolean containsNode(final ExplorerNode node) {
        return treeModel.containsNode(node);
    }

    public ExplorerNode getParent(final ExplorerNode child) {
        return treeModel.getParent(child);
    }

    public List<ExplorerNode> getChildren(final ExplorerNode parent) {
        return treeModel.getChildren(parent);
    }

    public boolean hasChildren(final String key) {
        return treeModel.hasChildren(key);
    }

    public List<DocRef> getChildren(final DocRef parent) {
        return treeModel.getChildren(parent);
    }

    public String getNodeKey(final ExplorerNode node) {
        return treeModel.getNodeKey(node);
    }

    public TreeModel createMutableCopy() {
        return treeModel.clone();
    }
}
