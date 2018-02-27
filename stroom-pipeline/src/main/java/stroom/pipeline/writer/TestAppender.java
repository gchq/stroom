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

package stroom.pipeline.writer;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;

@ConfigurableElement(type = "TestAppender", roles = {PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_DESTINATION}, icon = ElementIcons.STREAM)
public class TestAppender extends AbstractAppender {
    private OutputStream outputStream;

    @Inject
    public TestAppender(final ErrorReceiverProxy errorReceiverProxy) {
        super(errorReceiverProxy);
    }

    @Override
    protected OutputStream createOutputStream() {
        return outputStream;
    }

    public void setOutputStream(final OutputStream os) {
        outputStream = os;
    }
}
