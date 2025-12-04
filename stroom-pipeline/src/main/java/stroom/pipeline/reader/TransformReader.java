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

import java.io.FilterReader;
import java.io.Reader;

/**
 * An abstract class to implement a FilterReader extended with a
 * hasModifiedContent() method.
 */
public abstract class TransformReader extends FilterReader {
    protected boolean modified;

    protected TransformReader(final Reader in) {
        super(in);
        modified = false;
    }

    /**
     * Has stream content been modified?
     *
     * @return True if, and only if stream contents were transformed.
     */
    public boolean hasModifiedContent() {
        return modified;
    }
}
