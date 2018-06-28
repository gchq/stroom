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

import stroom.task.ServerTask;
import stroom.util.shared.VoidResult;

import java.io.InputStream;
import java.io.Writer;

public class CLITranslationTask extends ServerTask<VoidResult> {
    private final InputStream dataStream;
    private final InputStream metaStream;
    private final InputStream contextStream;
    private final Writer errorWriter;

    public CLITranslationTask(final InputStream dataStream,
                              final InputStream metaStream,
                              final InputStream contextStream,
                              final Writer errorWriter) {
        this.dataStream = dataStream;
        this.metaStream = metaStream;
        this.contextStream = contextStream;
        this.errorWriter = errorWriter;
    }

    InputStream getDataStream() {
        return dataStream;
    }

    InputStream getMetaStream() {
        return metaStream;
    }

    InputStream getContextStream() {
        return contextStream;
    }

    Writer getErrorWriter() {
        return errorWriter;
    }
}
