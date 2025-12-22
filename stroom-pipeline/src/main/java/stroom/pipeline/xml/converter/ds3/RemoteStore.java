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

package stroom.pipeline.xml.converter.ds3;

import org.xml.sax.SAXException;

public class RemoteStore implements Store {
    private static final int MAX_STORED_ITEMS = 1000;
    private static final int MAX_STRING_LENGTH = 1000;

    private final String debugId;
    private Buffer[] array = new Buffer[10];
    private int length = 0;

    public RemoteStore(final String debugId) {
        this.debugId = debugId;
    }

    @Override
    public void set(final int index, final Buffer val) throws SAXException {
        // Make sure we aren't storing too many strings in the array.
        if (index >= MAX_STORED_ITEMS) {
            final StringBuilder sb = new StringBuilder();
            sb.append("You cannot store more than ");
            sb.append(MAX_STORED_ITEMS);
            sb.append(" values in variable: ");
            sb.append(debugId);
            throw new SAXException(sb.toString());
        }

        // Grow the buffer if we need to.
        if (array.length <= index) {
            final Buffer[] tmp = new Buffer[index * 2];
            System.arraycopy(array, 0, tmp, 0, array.length);
            array = tmp;
        }

        // Record the new length.
        if (length <= index) {
            length = index + 1;
        }

        if (val == null || val.length() == 0) {
            // If the value is null or zero length store null.
            array[index] = null;

        } else if (val.length() >= MAX_STRING_LENGTH) {
            // If the value is too long then store null.
            array[index] = null;

            final StringBuilder sb = new StringBuilder();
            sb.append("You cannot store more than ");
            sb.append(MAX_STRING_LENGTH);
            sb.append(" characters in variable: ");
            sb.append(debugId);
            throw new SAXException(sb.toString());

        } else {
            // The variable is used remotely so store a copy of the trimmed
            // value as the current underlying buffer may change.
            array[index] = val.copy();
        }
    }

    @Override
    public Buffer get(final int index) {
        if (index >= 0 && length > index) {
            return array[index];
        }

        return null;
    }

    @Override
    public void clear() {
        for (int i = 0; i < length; i++) {
            array[i] = null;
        }
        length = 0;
    }
}
