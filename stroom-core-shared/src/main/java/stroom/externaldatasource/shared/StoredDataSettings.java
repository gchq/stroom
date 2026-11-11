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

import java.util.List;

/// What the settings of every type that holds its data in the document share: CSV and Excel.
public interface StoredDataSettings {

    /// @return True if the first row holds the column names, `null` meaning true.
    Boolean getHeader();

    /// @return The columns, in the order they appear in each row, or `null` for none.
    List<DataColumn> getColumns();
}
