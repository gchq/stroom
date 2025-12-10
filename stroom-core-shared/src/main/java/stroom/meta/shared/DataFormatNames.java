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

package stroom.meta.shared;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DataFormatNames {

    public DataFormatNames() {
    }

    /**
     * A fully formed XML document.
     */
    public static final String XML = "XML";
    /**
     * A fragment of XML, i.e. not a fully formed XML document.
     */
    public static final String XML_FRAGMENT = "XML_FRAGMENT";
    public static final String JSON = "JSON";
    public static final String YAML = "YAML";
    public static final String TOML = "TOML";
    public static final String INI = "INI";
    /**
     * Comma separated values with a header row
     */
    public static final String CSV = "CSV";
    /**
     * Comma separated values without a header row
     */
    public static final String CSV_NO_HEADER = "CSV_NO_HEADER";
    /**
     * Tab separated values, with a header row
     */
    public static final String TSV = "TSV";
    /**
     * Tab separated values, without a header row
     */
    public static final String TSV_NO_HEADER = "TSV_NO_HEADER";
    /**
     * Pipe separated values, with a header row
     */
    public static final String PSV = "PSV";
    /**
     * Pipe separated values, without a header row
     */
    public static final String PSV_NO_HEADER = "PSV_NO_HEADER";
    /**
     * Fixed width fields, with a header row
     */
    public static final String FIXED_WIDTH = "FIXED_WIDTH";
    /**
     * Fixed width fields, without a header row
     */
    public static final String FIXED_WIDTH_NO_HEADER = "FIXED_WIDTH_NO_HEADER";
    /**
     * A format not covered by the other format.
     */
    public static final String TEXT = "TEXT";
    /**
     * Syslog format
     */
    public static final String SYSLOG = "SYSLOG";

    /**
     * Used for config validation only as config may contain a superset of this set.
     */
    public static final Set<String> ALL_HARD_CODED_FORMAT_NAMES = new HashSet<>(Arrays.asList(
            XML,
            XML_FRAGMENT,
            JSON,
            YAML,
            TOML,
            INI,
            CSV,
            CSV_NO_HEADER,
            TSV,
            TSV_NO_HEADER,
            PSV,
            PSV_NO_HEADER,
            FIXED_WIDTH,
            FIXED_WIDTH_NO_HEADER,
            TEXT,
            SYSLOG));
}
