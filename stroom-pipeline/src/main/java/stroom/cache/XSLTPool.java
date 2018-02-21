/*
 * Copyright 2016 Crown Copyright
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

package stroom.cache;

import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pool.Pool;
import stroom.pool.PoolItem;

import java.util.List;

public interface XSLTPool extends Pool<XSLT, StoredXsltExecutable> {
    PoolItem<StoredXsltExecutable> borrowConfiguredTemplate(
            XSLT k, ErrorReceiver errorReceiver, LocationFactory locationFactory,
            List<PipelineReference> pipelineReferences, boolean usePool);
}
