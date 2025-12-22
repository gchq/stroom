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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.xml.converter.ds3.NodeFactory.NodeType;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

public abstract class Node {
    private final NodeFactory factory;
    private final Node[] childNodes;

    public Node(final VarMap varMap, final NodeFactory factory) {
        this.factory = factory;
        childNodes = new Node[factory.getChildNodes().size()];
        int i = 0;
        for (final NodeFactory node : factory.getChildNodes()) {
            childNodes[i++] = node.newInstance(varMap);
        }
    }

    public Node[] getChildNodes() {
        return childNodes;
    }

    void link(final VarMap varMap) {
        for (final Node node : childNodes) {
            node.link(varMap);
        }
    }

    public abstract NodeType getNodeType();

    public abstract boolean isExpression();

    public String getXML() {
        return factory.getXML();
    }

    public String getXPath() {
        return factory.getXPath();
    }

    public String getDebugId() {
        return factory.getDebugId();
    }

    @Override
    public String toString() {
        return factory.toString();
    }

    public void clear() {
        for (final Node node : childNodes) {
            node.clear();
        }
    }
}
