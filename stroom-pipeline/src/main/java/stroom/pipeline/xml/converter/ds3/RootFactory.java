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

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.xml.converter.ds3.ref.VarFactoryMap;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

public class RootFactory extends NodeFactory {

    public static final int DEFAULT_BUFFER_SIZE = 20000;
    public static final int MIN_BUFFER_SIZE = DEFAULT_BUFFER_SIZE;
    // The max buffer size of 1bn chars is 2Gb of memory. We should never need
    // more memory than this for processing a record.
    public static final int MAX_BUFFER_SIZE = 1000000000;

    private boolean ignoreErrors;
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    public RootFactory() {
        super(null, null);
    }

    public void compile() {
        final VarFactoryMap varFactoryMap = new VarFactoryMap();

        // Register all var nodes by id.
        for (final NodeFactory node : getChildNodes()) {
            node.register(varFactoryMap);
        }

        // Link all data nodes together.
        for (final NodeFactory node : getChildNodes()) {
            node.link(varFactoryMap, null);
        }

        setAttributes(new StringBuilder());
    }

    @Override
    public Root newInstance(final VarMap varMap) {
        return new Root(varMap, this);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.ROOT;
    }

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    public void setIgnoreErrors(final boolean ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(final int bufferSize) {
        if (bufferSize < MIN_BUFFER_SIZE) {
            throw ProcessException.create("Buffer size '" + bufferSize +
                    "' is less than min allowed buffer size of '" + MIN_BUFFER_SIZE + "'.");
        }

        if (bufferSize > MAX_BUFFER_SIZE) {
            throw ProcessException.create("Buffer size '" + bufferSize +
                    "' is greater than max allowed buffer size of '" + MAX_BUFFER_SIZE + "'.");
        }

        this.bufferSize = bufferSize;
    }
}
