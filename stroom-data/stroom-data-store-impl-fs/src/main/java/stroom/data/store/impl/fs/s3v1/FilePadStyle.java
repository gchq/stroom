/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs.s3v1;


import stroom.data.store.impl.fs.FsPrefixUtil;
import stroom.util.string.StringUtil;

import java.util.function.LongFunction;

/**
 * Different padding styles for numeric filenames
 */
public enum FilePadStyle {

    /**
     * A number left padded with zeros to ten digits.
     * <p>
     * This is the style used in proxy zips.
     * </p>
     */
    TEN_DIGITS(id -> StringUtil.zeroPad(id, 10)),

    /**
     * A number left padded with zeros to a multiple of three digits, e.g. 001, 001234.
     * <p>
     * This is the style used by {@link S3Target} and by {@link stroom.data.store.impl.fs.standard.FsTarget}
     * </p>
     */
    MULTIPLE_OF_THREE_DIGITS(FsPrefixUtil::padId),
    ;

    private final LongFunction<String> padFunc;

    FilePadStyle(final LongFunction<String> padFunc) {
        this.padFunc = padFunc;
    }

    /**
     * Pads the id using the configured style.
     */
    public String padId(final long id) {
        return padFunc.apply(id);
    }

    /**
     * Removes the padding and returns the ID as a long.
     */
    public long dePadLong(final String str) {
        return StringUtil.dePadLong(str);
    }

    /**
     * Removes the padding and returns the ID as an int.
     */
    public long dePadInteger(final String str) {
        return StringUtil.dePadInteger(str);
    }
}
