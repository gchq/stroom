/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.repo;

import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.shared.ModelStringUtil;

import java.nio.file.Path;

public class ZipInfo {
    private final Path path;
    private final FileSetKey key;
    private final Long uncompressedSize;
    private final Long compressedSize;
    private final Long lastModified;
    private final Integer zipEntryCount;

    ZipInfo(final Path path,
            final FileSetKey key,
            final Long uncompressedSize,
            final Long compressedSize,
            final Long lastModified,
            final Integer zipEntryCount) {
        this.path = path;
        this.key = key;
        this.uncompressedSize = uncompressedSize;
        this.compressedSize = compressedSize;
        this.lastModified = lastModified;
        this.zipEntryCount = zipEntryCount;
    }

    public Path getPath() {
        return path;
    }

    public FileSetKey getKey() {
        return key;
    }

    public Long getUncompressedSize() {
        return uncompressedSize;
    }

    public Long getCompressedSize() {
        return compressedSize;
    }

    public Long getLastModified() {
        return lastModified;
    }

    public Integer getZipEntryCount() {
        return zipEntryCount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(FileUtil.getCanonicalPath(path));
        if (key != null && key.getFeedName() != null) {
            sb.append("\n\tfeedName=");
            sb.append(key.getFeedName());
        }
        if (key != null && key.getTypeName() != null) {
            sb.append("\n\ttypeName=");
            sb.append(key.getTypeName());
        }
        if (uncompressedSize != null) {
            sb.append("\n\tuncompressedSize=");
            sb.append(ModelStringUtil.formatIECByteSizeString(uncompressedSize));
        }
        if (compressedSize != null) {
            sb.append("\n\tcompressedSize=");
            sb.append(ModelStringUtil.formatIECByteSizeString(compressedSize));
        }
        if (lastModified != null) {
            sb.append("\n\tlastModified=");
            sb.append(DateUtil.createNormalDateTimeString(lastModified));
        }
        if (zipEntryCount != null) {
            sb.append("\n\tzipEntryCount=");
            sb.append(zipEntryCount);
        }
        return sb.toString();
    }
}