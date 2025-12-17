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

import stroom.pipeline.xml.converter.ds3.ref.VarMap;

import java.util.Set;

public class AllFactory extends ExpressionFactory {
    public AllFactory(final NodeFactory parent, final String id) {
        this(parent, id, 0, -1, null);
    }

    public AllFactory(final NodeFactory parent, final String id, final int minMatch, final int maxMatch,
                      final Set<Integer> onlyMatch) {
        super(parent, id, minMatch, maxMatch, onlyMatch, -1);

        final StringBuilder sb = new StringBuilder();
        setAttributes(sb.toString());
    }

    @Override
    public All newInstance(final VarMap varMap) {
        return new All(varMap, this);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SPLIT;
    }
}
