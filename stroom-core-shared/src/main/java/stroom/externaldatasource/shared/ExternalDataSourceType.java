/*
 * Copyright 2026 Crown Copyright
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

package stroom.externaldatasource.shared;

import stroom.docref.HasDisplayValue;

/// The type of an [ExternalDataSourceDoc]: where its rows come from.
public enum ExternalDataSourceType implements HasDisplayValue {
    /// A database reached through a JDBC driver, using the settings in [JdbcConnectionSettings].
    JDBC("JDBC"),
    /// Amazon Athena, reached through the AWS SDK, using the settings in [AthenaConnectionSettings].
    ATHENA("Amazon Athena"),
    /// CSV held in the document, using the settings and data in [CsvSettings].
    CSV("CSV"),
    /// An Excel workbook held in the document, using the settings in [ExcelSettings].
    EXCEL("Excel");

    private final String displayValue;

    ExternalDataSourceType(final String displayValue) {
        this.displayValue = displayValue;
    }

    /// Gets the name of the type as shown to users.
    ///
    /// @return The display name.
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
