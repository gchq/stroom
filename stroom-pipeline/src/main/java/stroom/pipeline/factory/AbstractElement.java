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

package stroom.pipeline.factory;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.Terminator;
import stroom.util.shared.ElementId;

public abstract class AbstractElement implements Element {

    private Terminator terminator = Terminator.DEFAULT;
    private ElementId elementId;

    @Override
    public void startProcessing() {
    }

    @Override
    public void endProcessing() {
    }

    @Override
    public void startStream() {
    }

    @Override
    public void endStream() {
    }

    @Override
    public ElementId getElementId() {
        return elementId;
    }

    @Override
    public void setElementId(final ElementId elementId) {
        this.elementId = elementId;
    }

    protected void checkTermination() {
        try {
            terminator.checkTermination();
        } catch (final TaskTerminatedException e) {
            throw ProcessException.create(e.getMessage(), e);
        }
    }

    @Override
    public void setTerminator(final Terminator terminator) {
        this.terminator = terminator;
    }
}
