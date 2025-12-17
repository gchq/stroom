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

package stroom.docstore.shared;

@SuppressWarnings("TextBlockMigration") // Because GWT :-(
public enum DocumentTypeGroup {

    STRUCTURE(1,
            "Structure",
            ""),
    DATA_PROCESSING(2,
            "Data Processing",
            "Documents relating to the processing of data."),
    TRANSFORMATION(3,
            "Transformation",
            "Documents relating to the transformation of data."),
    SEARCH(4,
            "Search",
            "Documents relating to searching for data in Stroom."),
    INDEXING(5,
            "Indexing",
            "Documents relating to the process of adding data into an index, " +
                    "such as Lucene or Elasticsearch."),
    CONFIGURATION(6,
            "Configuration",
            "Documents that are used as configuration for other documents."),
    SYSTEM(100,
            "System",
            ""),
    INTERNAL(999,
            "Internal",
            "");

    private final int priority;
    private final String displayName;
    private final String description;

    DocumentTypeGroup(final int priority, final String displayName, final String description) {
        this.priority = priority;
        this.displayName = displayName;
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
