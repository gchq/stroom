/*
 * Copyright 2019 Crown Copyright
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

package stroom.datasource.api.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FieldTypes {
    public static final String ID = "Id";
    public static final String BOOLEAN = "Boolean";
    public static final String INTEGER = "Integer";
    public static final String LONG = "Long";
    public static final String FLOAT = "Float";
    public static final String DOUBLE = "Double";
    public static final String DATE = "Date";
    public static final String TEXT = "Text";
    public static final String DOC_REF = "DocRef";

    public static final List<String> TYPES = new ArrayList<>(Arrays.asList(ID, BOOLEAN, INTEGER, LONG, FLOAT, DOUBLE, DATE, TEXT, DOC_REF));
}
