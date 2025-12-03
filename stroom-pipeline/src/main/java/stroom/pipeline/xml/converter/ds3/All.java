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

public class All extends Expression implements Match {
    private CharSequence cs;

    public All(final VarMap varMap, final AllFactory factory) {
        super(varMap, factory);
    }

    @Override
    public void setInput(final CharSequence cs) {
        this.cs = cs;
    }

    @Override
    public Match match() {
        return this;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.ALL;
    }

    @Override
    public int start() {
        return 0;
    }

    @Override
    public int start(final int group) {
        if (group != 0) {
            throw new IndexOutOfBoundsException("No group " + group);
        }

        return 0;
    }

    @Override
    public int end() {
        return cs.length();
    }

    @Override
    public int end(final int group) {
        if (group != 0) {
            throw new IndexOutOfBoundsException("No group " + group);
        }

        return cs.length();
    }

    @Override
    public Buffer filter(final Buffer buffer, final int group) {
        if (group != 0) {
            throw new IndexOutOfBoundsException("No group " + group);
        }

        return buffer.unsafeCopy();
    }
}
