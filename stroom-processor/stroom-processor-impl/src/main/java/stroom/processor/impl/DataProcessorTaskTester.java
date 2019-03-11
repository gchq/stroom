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

package stroom.processor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.Source;
import stroom.processor.api.DataProcessorTaskExecutor;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTask;

public class DataProcessorTaskTester implements DataProcessorTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataProcessorTaskTester.class);

    @Override
    public void exec(final Processor processor,
                     final ProcessorFilter processorFilter,
                     final ProcessorFilterTask processorFilterTask,
                     final Source source) {
        LOGGER.info("exec() - Processing stream {}", source.getMeta());
    }
}
