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

package stroom.streamtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.streamstore.store.api.StreamSource;
import stroom.streamtask.shared.Processor;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.streamtask.shared.ProcessorFilterTask;

public class StreamProcessorTaskTester implements StreamProcessorTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamProcessorTaskTester.class);

    @Override
    public void exec(final Processor streamProcessor, final ProcessorFilter streamProcessorFilter,
                     final ProcessorFilterTask streamTask, final StreamSource streamSource) {
        LOGGER.info("exec() - Processing stream {}", streamSource.getStream());
    }
}
