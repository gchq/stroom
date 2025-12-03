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
import stroom.pipeline.xml.converter.ds3.ref.Ref;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

public class Data extends StoreNode {
    private final DataFactory factory;

    private Ref name;
    private Ref value;

    public Data(final VarMap varMap, final DataFactory factory) {
        super(varMap, factory);
        this.factory = factory;
    }

    @Override
    void link(final VarMap varMap) {
        // Link this item.
        name = factory.getRefName().createRef(varMap, this);
        value = factory.getRefValue().createRef(varMap, this);

        // Link children.
        super.link(varMap);
    }

    public Buffer lookupName(final int matchCount) {
        return name.lookup(matchCount);
    }

    public Buffer lookupValue(final int matchCount) {
        return value.lookup(matchCount);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.DATA;
    }
}
