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

import stroom.pipeline.xml.converter.ds3.GroupFactory.MatchOrder;
import stroom.pipeline.xml.converter.ds3.NodeFactory.NodeType;
import stroom.pipeline.xml.converter.ds3.ref.Ref;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

public class Group extends StoreNode {
    private final GroupFactory factory;

    private final boolean reverse;
    private final MatchOrder matchOrder;
    private final boolean ignoreErrors;

    private Ref value;

    public Group(final VarMap varMap, final GroupFactory factory) {
        super(varMap, factory);
        this.factory = factory;

        this.reverse = factory.isReverse();
        this.matchOrder = factory.getMatchOrder();
        this.ignoreErrors = factory.isIgnoreErrors();
    }

    @Override
    void link(final VarMap varMap) {
        // Link this item.
        value = factory.getRefValue().createRef(varMap, this);

        // Link children.
        super.link(varMap);
    }

    public Buffer lookupValue(final int matchCount) {
        return value.lookup(matchCount);
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

    @Override
    public NodeType getNodeType() {
        return NodeType.GROUP;
    }
}
