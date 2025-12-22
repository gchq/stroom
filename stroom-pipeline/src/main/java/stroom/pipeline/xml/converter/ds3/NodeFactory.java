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

import stroom.pipeline.xml.converter.ds3.ref.VarFactoryMap;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class NodeFactory {

    private final String id;
    private final String xPath;
    private final List<NodeFactory> childNodes;
    private String xml;
    private String debugId;

    public NodeFactory(final NodeFactory parent, final String id) {
        this.id = id;

        childNodes = new ArrayList<>();

        final StringBuilder sb = new StringBuilder();
        if (parent != null) {
            int index = -1;
            for (final NodeFactory node : parent.getChildNodes()) {
                if (node.getNodeType() == getNodeType()) {
                    index++;
                }
            }
            if (parent.getXPath().length() > 0) {
                sb.append(parent.getXPath());
                sb.append("/");
            }
            sb.append(getNodeType().getName());
            sb.append("[");
            sb.append(index + 1);
            sb.append("]");

            parent.addNode(this);
        }

        xPath = sb.toString();
    }

    private void addNode(final NodeFactory node) {
        childNodes.add(node);
    }

    public List<NodeFactory> getChildNodes() {
        return childNodes;
    }

    void register(final VarFactoryMap varFactoryMap) {
        varFactoryMap.checkUniqueId(this);

        for (final NodeFactory node : childNodes) {
            node.register(varFactoryMap);
        }
    }

    void link(final VarFactoryMap varFactoryMap, final Set<String> localVars) {
        Set<String> vars = null;
        if (localVars != null) {
            vars = new HashSet<>(localVars);
        }

        for (final NodeFactory node : childNodes) {
            if (node.getNodeType() == NodeType.VAR) {
                // If this node is a var then store its id as it is a local
                // variable.
                if (node.getId() != null) {
                    if (vars == null) {
                        vars = new HashSet<>();
                    }
                    vars.add(node.getId());
                }
            } else {
                // Otherwise continue linking child nodes.
                node.link(varFactoryMap, vars);
            }
        }
    }

    abstract Node newInstance(VarMap varMap);

    void setAttributes(final StringBuilder attributes) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(getNodeType().getName());
        if (id != null) {
            sb.append(" id=\"");
            sb.append(id);
            sb.append("\"");
        }
        sb.append(attributes);
        sb.append(">");
        xml = sb.toString();

        debugId = xPath + " : " + xml;
    }

    public String getId() {
        return id;
    }

    public String getXML() {
        return xml;
    }

    public String getXPath() {
        return xPath;
    }

    public String getDebugId() {
        return debugId;
    }

    void toString(final StringBuilder sb, final String pad) {
        sb.append(pad);
        sb.append(xml);
        if (getChildNodes().size() > 0) {
            sb.append("\n");
            for (final NodeFactory node : getChildNodes()) {
                node.toString(sb, pad + "  ");
            }
            sb.append(pad);

            sb.append("</");
            sb.append(getNodeType().getName());
            sb.append(">\n");
        } else {
            sb.setLength(sb.length() - 1);
            sb.append(" />\n");
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb, "");
        return sb.toString();
    }

    public abstract NodeType getNodeType();

    public enum NodeType {
        ROOT("dataSplitter"), SPLIT("split"), REGEX("regex"), ALL("all"), GROUP("group"), VAR("var"), DATA("data");

        private final String name;

        NodeType(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
