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

package stroom.pipeline.writer;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;

import java.io.OutputStream;

public class OutputStreamAppender extends AbstractAppender {

    private final OutputStream outputStream;

    public OutputStreamAppender(final ErrorReceiverProxy errorReceiverProxy,
                                final OutputStream outputStream) {
        super(errorReceiverProxy);
        this.outputStream = outputStream;
    }

    @Override
    protected Output createOutput() {
        return new BasicOutput(outputStream);
    }

    @Override
    long getCurrentOutputSize() {
        return 0;
    }
}
