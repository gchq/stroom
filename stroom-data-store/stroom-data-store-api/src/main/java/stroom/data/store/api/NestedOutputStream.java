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

package stroom.data.store.api;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream that works like a zip output stream in that you can add nested items
 * within the main stream.
 */
public abstract class NestedOutputStream extends OutputStream {
    private boolean requireForcedClose;

    /**
     * You must call this before writing data to add the next nested entry
     */
    public abstract void putNextEntry() throws IOException;

    /**
     * Close this when your done so you can call putNextEntry() or close() next
     */
    public abstract void closeEntry() throws IOException;

    /**
     * This method exists to force subclasses to implement their own close
     * behaviour.
     */
    protected abstract void doClose() throws IOException;

    @Override
    public void close() throws IOException {
        if (!requireForcedClose) {
            doClose();
        }
    }

    public void forceClose() throws IOException {
        doClose();
    }

    public void setRequireForcedClose(boolean requireForcedClose) {
        this.requireForcedClose = requireForcedClose;
    }
}
