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

package stroom.widget.util.client;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.function.Function;

public class TableUtil {

    public static <T> String getString(final T object, final Function<T, String> function) {
        if (object == null) {
            return "";
        }
        final String string = function.apply(object);
        if (string == null) {
            return "";
        }
        return string;
    }

    public static <T> SafeHtml getSafeHtml(final T object, final Function<T, String> function) {
        if (object == null) {
            return SafeHtmlUtil.NBSP;
        }
        final String string = function.apply(object);
        if (string == null) {
            return SafeHtmlUtil.NBSP;
        }
        return SafeHtmlUtils.fromString(string);
    }
}
