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

package stroom.query.language.functions;


abstract class Calculator {

    Val calc(final Val current, final Val value) {
        try {
            if (value.type().isError() ||
                current.type().isNull()) {
                return value;
            } else if (current.type().isError()) {
                return current;
            }

            final Double cur = current.toDouble();
            final Double val = value.toDouble();
            if (val == null) {
                return current;
            }
            if (cur == null) {
                return value;
            }
            return ValDouble.create(op(cur, val));

        } catch (final RuntimeException e) {
            return ValErr.create(e.getMessage());
        }
    }

    double op(final double cur, final double val) {
        return cur;
    }
}
