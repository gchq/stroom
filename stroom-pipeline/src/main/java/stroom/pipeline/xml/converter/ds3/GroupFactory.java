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

import stroom.pipeline.xml.converter.ds3.ref.RefParser;
import stroom.pipeline.xml.converter.ds3.ref.RefResolver;
import stroom.pipeline.xml.converter.ds3.ref.VarFactoryMap;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

import java.util.Set;

public class GroupFactory extends StoreFactory {

    private final boolean reverse;
    private final boolean ignoreErrors;
    private final MatchOrder matchOrder;
    private String value;
    private RefResolver refValue;

    public GroupFactory(final NodeFactory parent, final String id) {
        this(parent, id, null, false, MatchOrder.SEQUENCE, false);
    }

    public GroupFactory(final NodeFactory parent, final String id, final String value, final boolean reverse,
                        final MatchOrder matchOrder, final boolean ignoreErrors) {
        super(parent, id);
        this.value = value;
        this.reverse = reverse;
        this.matchOrder = matchOrder;
        this.ignoreErrors = ignoreErrors;

        final StringBuilder sb = new StringBuilder();
        if (value != null) {
            sb.append(" value=\"");
            sb.append(value);
            sb.append("\"");
        }
        setAttributes(sb);

        // As this is a group make sure we at least reference ourselves.
        if (this.value == null) {
            this.value = String.valueOf(RefParser.REF_CHAR);
        }
    }

    public String getValue() {
        return value;
    }

    @Override
    void link(final VarFactoryMap varFactoryMap, final Set<String> localVars) {
        // Link this node.
        refValue = RefResolver.create(varFactoryMap, this, value, localVars);

        // Link child nodes.
        super.link(varFactoryMap, localVars);
    }

    public RefResolver getRefValue() {
        return refValue;
    }

    public Group newInstance(final VarMap varMap) {
        return new Group(varMap, this);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.GROUP;
    }

    public boolean isReverse() {
        return reverse;
    }

    public MatchOrder getMatchOrder() {
        return matchOrder;
    }

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    public enum MatchOrder {
        SEQUENCE, ANY
    }
}
