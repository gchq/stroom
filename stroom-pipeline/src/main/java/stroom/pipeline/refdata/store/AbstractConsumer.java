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

package stroom.pipeline.refdata.store;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConsumer.class);

    protected final Receiver receiver;
    protected final PipelineConfiguration pipelineConfiguration;

    public AbstractConsumer(
            final PipelineConfiguration pipelineConfiguration,
            final Receiver receiver) {

        this.pipelineConfiguration = pipelineConfiguration;
        this.receiver = receiver;
    }
}
