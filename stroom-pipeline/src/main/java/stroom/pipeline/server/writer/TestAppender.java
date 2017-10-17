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

package stroom.pipeline.server.writer;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;

@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "TestAppender", roles = {PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_DESTINATION}, icon = ElementIcons.STREAM)
public class TestAppender extends AbstractAppender {
    private OutputStream outputStream;

    @Inject
    public TestAppender(final ErrorReceiverProxy errorReceiverProxy,
                        final OutputStream outputStream) {
        super(errorReceiverProxy);
        this.outputStream = outputStream;
    }

    @Override
    protected OutputStream createOutputStream() throws IOException {
        return outputStream;
    }

    public void setOutputStream(final OutputStream os) {
        outputStream = os;
    }
}
