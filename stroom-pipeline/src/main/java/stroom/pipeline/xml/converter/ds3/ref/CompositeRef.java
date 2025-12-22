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

import java.util.List;

public class CompositeRef implements Ref {
    private final List<Ref> refs;

    public CompositeRef(final List<Ref> refs) {
        this.refs = refs;
    }

    @Override
    public Buffer lookup(final int matchCount) {
        char[] chars = null;
        for (final Ref ref : refs) {
            final Buffer part = ref.lookup(matchCount);
            if (part != null) {
                final char[] arr = part.toCharArray();
                if (chars == null) {
                    chars = arr;
                } else {
                    final char[] tmp = new char[chars.length + arr.length];
                    System.arraycopy(chars, 0, tmp, 0, chars.length);
                    System.arraycopy(arr, 0, tmp, chars.length, arr.length);
                    chars = tmp;
                }
            }
        }
        return new CharBuffer(chars, 0, chars.length);
    }
}
