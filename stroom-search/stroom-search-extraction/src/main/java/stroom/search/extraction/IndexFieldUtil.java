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

package stroom.search.extraction;

import stroom.query.api.datasource.IndexField;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.util.date.DateUtil;

public class IndexFieldUtil {

    public static Val convertValue(final IndexField indexField, final String value) {
        switch (indexField.getFldType()) {
            case LONG, ID -> {
                final long val = Long.parseLong(value);
                return ValLong.create(val);
            }
            case BOOLEAN -> {
                final boolean val = Boolean.parseBoolean(value);
                return ValBoolean.create(val);
            }
            case INTEGER -> {
                final int val = Integer.parseInt(value);
                return ValInteger.create(val);
            }
            case FLOAT -> {
                final float val = Float.parseFloat(value);
                return ValFloat.create(val);
            }
            case DOUBLE -> {
                final double val = Double.parseDouble(value);
                return ValDouble.create(val);
            }
            case DATE -> {
                final long val = DateUtil.parseNormalDateTimeString(value);
                return ValDate.create(val);
            }
            case TEXT -> {
                return ValString.create(value);
            }
        }
        return null;
    }
}
