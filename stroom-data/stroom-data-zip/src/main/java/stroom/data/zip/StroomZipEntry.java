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

package stroom.data.zip;

import stroom.util.io.FileName;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.Objects;

public class StroomZipEntry {

    private static final String SINGLE_ENTRY_ZIP_BASE_NAME = "001";
    public static final String REPO_EXTENSION_DELIMITER = ",";

    public static final StroomZipEntry SINGLE_DATA_ENTRY =
            createFromBaseName(SINGLE_ENTRY_ZIP_BASE_NAME, StroomZipFileType.DATA);
    public static final StroomZipEntry SINGLE_META_ENTRY =
            createFromBaseName(SINGLE_ENTRY_ZIP_BASE_NAME, StroomZipFileType.META);

    private final String baseName;
    private final String fullName;
    private final StroomZipFileType stroomZipFileType;

    private StroomZipEntry(final String baseName,
                           final String fullName,
                           final StroomZipFileType stroomZipFileType) {
        this.baseName = baseName;
        this.fullName = fullName;
        if (baseName != null && fullName != null && !fullName.startsWith(baseName)) {
            throw new RuntimeException(LogUtil.message("baseName '{}' is not a prefix of fullName '{}'",
                    baseName, fullName));
        }
        this.stroomZipFileType = stroomZipFileType;
    }

    public static StroomZipEntry createFromFileName(final String fileName) {
        if (fileName.endsWith(".")) {
            // We can't cope with zip entries that end with `.` as we are splitting base name and extension.
            throw new RuntimeException("Zip entries ending with '.' are not supported");
        }

        FileName fn = FileName.parse(fileName);
        if (!StroomZipFileType.isKnownExtension(fn.getExtension())) {
            // Extension is not a known one so treat the whole fileName as the baseName
            // e.g. a fileName with dots in, '2023-11-15.xyz.1001' or no extension at all '001'.
            fn = FileName.fromParts(fileName, null);
        }
        if (NullSafe.contains(fn.getExtension(), REPO_EXTENSION_DELIMITER)) {
            // We can't cope with zip entries that have extensions that contain `,` as we delimit extensions in the DB.
            throw new RuntimeException("Zip entries with extensions containing ',' are not supported");
        }
        final StroomZipFileType stroomZipFileType = StroomZipFileType.fromExtension(fn.getExtension());
        return new StroomZipEntry(fn.getBaseName(), fn.getFullName(), stroomZipFileType);
    }

    public static StroomZipEntry createFromBaseName(final String baseName,
                                                    final StroomZipFileType stroomZipFileType) {
        return new StroomZipEntry(
                baseName,
                baseName + stroomZipFileType.getDotExtension(),
                stroomZipFileType);
    }

    public StroomZipEntry cloneWithNewBaseName(final String newBaseName) {
        if (!this.baseName.startsWith(newBaseName)) {
            throw new RuntimeException(LogUtil.message("newBaseName '{}' is not a prefix of baseName '{}'",
                    newBaseName, baseName));
        }
        return new StroomZipEntry(newBaseName, fullName, stroomZipFileType);
    }

    public String getBaseName() {
        return baseName;
    }

    public StroomZipFileType getStroomZipFileType() {
        return stroomZipFileType;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StroomZipEntry zipEntry = (StroomZipEntry) o;
        return Objects.equals(baseName, zipEntry.baseName) && Objects.equals(fullName,
                zipEntry.fullName) && stroomZipFileType == zipEntry.stroomZipFileType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseName, fullName, stroomZipFileType);
    }

    @Override
    public String toString() {
        return "'" + fullName + "' (baseName: '"
               + baseName + "', type: "
               + stroomZipFileType + ")";
    }

    /**
     * @return True if the fullName has a known stroom extension
     */
    public boolean hasKnownExtension() {
        return stroomZipFileType.hasExtension(fullName);
    }
}
