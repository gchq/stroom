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

package stroom.query.shared;

import stroom.util.shared.NullSafe;

public enum InsertType {
    PLAIN_TEXT(true),
    SNIPPET(true),
    BLANK(false),
    NOT_INSERTABLE(false);

    private final boolean isInsertable;

    InsertType(final boolean isInsertable) {
        this.isInsertable = isInsertable;
    }

    public boolean isInsertable() {
        return isInsertable;
    }

    /**
     * @return SNIPPET or BLANK depending on whether snippet is blank or not.
     */
    public static InsertType snippet(final String snippet) {
        return NullSafe.isBlankString(snippet)
                ? InsertType.BLANK
                : InsertType.SNIPPET;
    }

    /**
     * @return PLAIN_TEXT or BLANK depending on whether text is blank or not.
     */
    public static InsertType plainText(final String text) {
        return NullSafe.isBlankString(text)
                ? InsertType.BLANK
                : InsertType.PLAIN_TEXT;
    }
}
