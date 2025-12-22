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

package stroom.pipeline.xml.converter.ds3.ref;

import stroom.pipeline.xml.converter.ds3.Buffer;
import stroom.pipeline.xml.converter.ds3.CharBuffer;

public class TextRefFactory extends RefFactory {
    public TextRefFactory(final String reference, final String section) {
        super(reference, section);
    }

    public Ref createRef() {
        final char[] nameArr = getSection().toCharArray();
        final Buffer nameBuffer = new CharBuffer(nameArr, 0, nameArr.length);
        return new TextRef(this, nameBuffer);
    }

    String getText() {
        return getSection();
    }

    @Override
    public boolean isText() {
        return true;
    }
}
