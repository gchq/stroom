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

package stroom.data.store.impl;

import stroom.util.shared.ModelStringUtil;

import java.io.Serializable;

/**
 * Holds the settings for import/export.
 */
public class DataDownloadSettings implements Serializable {

    private static final long serialVersionUID = -6976894005366894145L;

    // By default we only export 50k segments or 1GB file and we don't write
    // multiple files.
    private Long maxFileParts = ModelStringUtil.parseNumberString("50K");
    private Long maxFileSize = ModelStringUtil.parseNumberString("1G");
    private boolean multipleFiles = false;

    public Long getMaxFileParts() {
        return maxFileParts;
    }

    public void setMaxFileParts(final Long maxSegmentsPerFile) {
        this.maxFileParts = maxSegmentsPerFile;
    }

    public Long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(final Long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public boolean isMultipleFiles() {
        return multipleFiles;
    }

    public void setMultipleFiles(final boolean multipleFiles) {
        this.multipleFiles = multipleFiles;
    }

}
