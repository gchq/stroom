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

package stroom.dashboard.shared;

import java.util.ArrayList;
import java.util.List;

public class ParamUtil {
    public static String getParam(final String string) {
        if (string == null) {
            return null;
        }

        int end = 0;
        int start = 0;

        start = string.indexOf("${", end);
        if (start != -1) {
            end = string.indexOf("}", start);
            if (end != -1) {
                final String param = string.substring(start + 2, end);
                return param;
            }
        }

        return null;
    }

    public static List<String> getParams(final String string) {
        if (string == null) {
            return null;
        }

        final List<String> params = new ArrayList<String>();
        int end = 0;
        int start = 0;

        while (start != -1) {
            start = string.indexOf("${", end);
            if (start != -1) {
                end = string.indexOf("}", start);
                if (end != -1) {
                    final String param = string.substring(start + 2, end);
                    params.add(param);
                }
            }
        }

        return params;
    }

    public static String makeParam(final String param) {
        return "${" + param + "}";
    }
}
