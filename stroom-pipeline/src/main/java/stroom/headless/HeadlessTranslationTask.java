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

package stroom.headless;

import java.io.InputStream;

import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

public class HeadlessTranslationTask extends ServerTask<VoidResult> {
    private final InputStream dataStream;
    private final InputStream metaStream;
    private final InputStream contextStream;
    private final HeadlessFilter headlessFilter;

    public HeadlessTranslationTask(final InputStream dataStream, final InputStream metaStream,
            final InputStream contextStream, final HeadlessFilter headlessFilter) {
        this.dataStream = dataStream;
        this.metaStream = metaStream;
        this.contextStream = contextStream;
        this.headlessFilter = headlessFilter;
    }

    public InputStream getDataStream() {
        return dataStream;
    }

    public InputStream getMetaStream() {
        return metaStream;
    }

    public InputStream getContextStream() {
        return contextStream;
    }

    public HeadlessFilter getHeadlessFilter() {
        return headlessFilter;
    }
}
