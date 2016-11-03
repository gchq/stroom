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

package stroom.dashboard.expression;

import java.text.ParseException;

public class ParamFactory {
    public Object create(final FieldIndexMap fieldIndexMap, final CharSlice slice) throws ParseException {
        if (slice.startsWith("'") && slice.endsWith("'")) {
            return TypeConverter.unescape(slice.toString());

        } else {
            final int dollar = slice.indexOf("$");
            if (dollar != -1) {
                final int start = slice.indexOf("{");
                final int end = slice.indexOf("}");
                if (dollar == 0 && start == 1 && end == slice.length() - 1) {
                    final String fieldName = slice.substring(start + 1, end);
                    final int fieldIndex = fieldIndexMap.create(fieldName);
                    return new Ref(slice.toString(), fieldIndex);

                } else {
                    throw new ParseException("Invalid reference: '" + slice + "'", slice.getOffset());
                }

            } else {
                try {
                    final String str = slice.toString();
                    final double dbl = Double.parseDouble(str);
                    return Double.valueOf(dbl);

                } catch (final NumberFormatException e) {
                    throw new ParseException("Unable to parse '" + slice + "' as number", slice.getOffset());
                }
            }
        }
    }
}
