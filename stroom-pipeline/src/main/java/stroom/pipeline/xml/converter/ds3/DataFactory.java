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

import stroom.pipeline.xml.converter.ds3.ref.RefResolver;
import stroom.pipeline.xml.converter.ds3.ref.VarFactoryMap;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

import java.util.Set;

public class DataFactory extends StoreFactory {
    private final String name;
    private final String value;
    private RefResolver refName;
    private RefResolver refValue;

    public DataFactory(final NodeFactory parent, final String id) {
        this(parent, id, null, null);
    }

    public DataFactory(final NodeFactory parent, final String id, final String name) {
        this(parent, id, name, null);
    }

    public DataFactory(final NodeFactory parent, final String id, final String name, final String value) {
        super(parent, id);
        this.name = name;
        this.value = value;

        final StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(" name=\"");
            sb.append(name);
            sb.append("\"");
        }
        if (value != null) {
            sb.append(" value=\"");
            sb.append(value);
            sb.append("\"");
        }
        setAttributes(sb);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    void link(final VarFactoryMap varFactoryMap, final Set<String> localVars) {
        // Link this node.
        refName = RefResolver.create(varFactoryMap, this, name, localVars);
        refValue = RefResolver.create(varFactoryMap, this, value, localVars);

        // Link child nodes.
        super.link(varFactoryMap, localVars);
    }

    public RefResolver getRefName() {
        return refName;
    }

    public RefResolver getRefValue() {
        return refValue;
    }

    @Override
    public Data newInstance(final VarMap varMap) {
        return new Data(varMap, this);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.DATA;
    }
}
