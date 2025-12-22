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

package stroom.query.impl;

import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.TokenException;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TokenError;

public class TokenExceptionUtil {


    public static TokenError toTokenError(final TokenException tokenException) {
        final AbstractToken token = tokenException.getToken();
        final char[] chars = token.getChars();
        int lineNo = 1;
        int colNo = 0;
        int index = 0;
        for (; index < token.getStart(); index++) {
            final char c = chars[index];
            if (c == '\n') {
                lineNo++;
                colNo = 0;
            } else if (c == '\r') {
                // Ignore.
            } else {
                colNo++;
            }
        }
        final DefaultLocation from = new DefaultLocation(lineNo, colNo);
        for (; index <= token.getEnd(); index++) {
            final char c = chars[index];
            if (c == '\n') {
                lineNo++;
                colNo = 0;
            } else if (c == '\r') {
                // Ignore.
            } else {
                colNo++;
            }
        }
        final DefaultLocation to = new DefaultLocation(lineNo, colNo);
        return new TokenError(from, to, tokenException.getMessage());
    }
}
