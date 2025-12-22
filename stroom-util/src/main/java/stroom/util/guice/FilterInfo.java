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

package stroom.util.guice;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FilterInfo {

    private final String name;
    private final String urlPattern;
    private final Map<String, String> initParameters = new HashMap<>();

    public FilterInfo(final String name,
                      final String urlPattern) {
        this.name = name;
        this.urlPattern = urlPattern;
    }

    public FilterInfo addparameter(final String name, final String value) {
        initParameters.put(name, value);
        return this;
    }

    public String getName() {
        return name;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public Map<String, String> getInitParameters() {
        return initParameters;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FilterInfo that = (FilterInfo) o;
        return name.equals(that.name) &&
                urlPattern.equals(that.urlPattern) &&
                initParameters.equals(that.initParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, urlPattern, initParameters);
    }

    @Override
    public String toString() {
        return name;
    }
}
