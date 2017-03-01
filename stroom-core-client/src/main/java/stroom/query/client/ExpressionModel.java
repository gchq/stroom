/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.client;

import stroom.query.api.ExpressionBuilder;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
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
            final ExpressionOperator source = (ExpressionOperator) item;
            final ExpressionBuilder dest = new ExpressionBuilder(source.getEnabled(), source.getOp());
            addChildrenFromTree(source, dest, tree);
            return dest.build();
        }
        return null;
    }

    private void addChildrenToTree(final DefaultTreeForTreeLayout<ExpressionItem> tree,
                                   final ExpressionOperator parent) {
        if (parent != null) {
            final List<ExpressionItem> children = parent.getChildren();
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

    private void addChildrenFromTree(final ExpressionOperator source, final ExpressionBuilder dest, final DefaultTreeForTreeLayout<ExpressionItem> tree) {
        final List<ExpressionItem> children = tree.getChildren(source);
        if (children != null) {
            for (final ExpressionItem child : children) {
                if (child instanceof ExpressionOperator) {
                    final ExpressionOperator childSource = (ExpressionOperator) child;
                    final ExpressionBuilder childDest = dest.addOperator(childSource.getEnabled(), childSource.getOp());
                    addChildrenFromTree(childSource, childDest, tree);
                } else if (child instanceof ExpressionTerm) {
                    dest.addTerm((ExpressionTerm) child);
                }
            }
        }
    }
}
