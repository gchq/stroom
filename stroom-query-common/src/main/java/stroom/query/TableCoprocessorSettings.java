/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query;

import stroom.query.api.DocRef;
import stroom.query.api.TableSettings;

public class TableCoprocessorSettings implements CoprocessorSettings {
    private static final long serialVersionUID = -4916050910828000494L;

    private TableSettings tableSettings;

    public TableCoprocessorSettings(final TableSettings tableSettings) {
        this.tableSettings = tableSettings;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    @Override
    public boolean extractValues() {
        return tableSettings.extractValues();
    }

    @Override
    public DocRef getExtractionPipeline() {
        return tableSettings.getExtractionPipeline();
    }
}
