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

public class LocalStore implements Store {
    private Buffer value;

    @Override
    public void set(final int index, final Buffer value) throws SAXException {
        if (value == null) {
            this.value = null;
        } else {
            this.value = value.unsafeCopy();
        }
    }

    @Override
    public Buffer get(final int index) {
        return value;
    }

    @Override
    public void clear() {
        value = null;
    }
}
