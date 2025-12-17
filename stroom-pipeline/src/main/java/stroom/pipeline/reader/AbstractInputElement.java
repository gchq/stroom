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

package stroom.pipeline.reader;

import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.factory.TakesInput;
import stroom.pipeline.factory.Target;
import stroom.task.api.Terminator;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractInputElement extends AbstractIOElement implements TakesInput, Target {

    @Override
    public void addTarget(final Target target) {
        if (target != null) {
            if (!(target instanceof DestinationProvider) && !(target instanceof TakesInput)) {
                throw new PipelineFactoryException("Attempt to link to an element that does not accept input: "
                        + getElementId() + " > " + target.getElementId());
            }
            super.addTarget(target);
        }
    }

    @Override
    public void setInputStream(final InputStream inputStream, final String encoding) throws IOException {
        super.setInputStream(insertFilter(inputStream, encoding), encoding);
    }

    protected InputStream insertFilter(final InputStream inputStream, final String encoding) {
        return inputStream;
    }

    @Override
    public void setTerminator(final Terminator terminator) {
    }
}
