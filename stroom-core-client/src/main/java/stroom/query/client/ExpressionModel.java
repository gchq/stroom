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

package stroom.query.client;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import java.util.List;

public class ExpressionModel {
    public DefaultTreeForTreeLayout<ExpressionItem> getTreeFromExpression(final ExpressionOperator expression) {
        final DefaultTreeForTreeLayout<ExpressionItem> tree = new DefaultTreeForTreeLayout<>(expression);
        addChildrenToTree(tree, expression);
        return tree;
    }

    public ExpressionOperator getExpressionFromTree(final DefaultTreeForTreeLayout<ExpressionItem> tree) {
        final ExpressionItem item = tree.getRoot();
        if (item != null && item instanceof ExpressionOperator) {
            final ExpressionOperator parent = (ExpressionOperator) item;
            addChildrenFromTree(tree, parent);
            return parent;
        }
        return null;
    }

    private void addChildrenToTree(final DefaultTreeForTreeLayout<ExpressionItem> tree,
                                   final ExpressionOperator parent) {
        if (parent != null) {
            final ExpressionItem[] children = parent.getChildren();
            if (children != null) {
                for (final ExpressionItem child : children) {
                    tree.addChild(parent, child);
                    if (child instanceof ExpressionOperator) {
                        addChildrenToTree(tree, (ExpressionOperator) child);
                    }
                }
            }
        }
    }

    private void addChildrenFromTree(final DefaultTreeForTreeLayout<ExpressionItem> tree,
                                     final ExpressionOperator parent) {
        parent. clear();

        final List<ExpressionItem> children = tree.getChildren(parent);
        if (children != null) {
            for (final ExpressionItem child : children) {
                parent.add(child);
                if (child instanceof ExpressionOperator) {
                    addChildrenFromTree(tree, (ExpressionOperator) child);
                }
            }
        }
    }
}
