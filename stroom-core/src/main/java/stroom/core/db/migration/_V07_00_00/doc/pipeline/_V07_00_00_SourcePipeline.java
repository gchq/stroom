/*
 * Copyright 2017 Crown Copyright
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

package stroom.core.db.migration._V07_00_00.doc.pipeline;

import stroom.core.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.core.db.migration._V07_00_00.docref._V07_00_00_SharedObject;
import stroom.core.db.migration._V07_00_00.docstore.shared._V07_00_00_DocRefUtil;

public class _V07_00_00_SourcePipeline implements _V07_00_00_SharedObject {
    private static final long serialVersionUID = -3209898449831302066L;

    private _V07_00_00_DocRef pipeline;

    public _V07_00_00_SourcePipeline() {
    }

    public _V07_00_00_SourcePipeline(final _V07_00_00_PipelineDoc pipeline) {
        this.pipeline = _V07_00_00_DocRefUtil.create(pipeline);
    }

    public _V07_00_00_DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final _V07_00_00_DocRef pipeline) {
        this.pipeline = pipeline;
    }
}
