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

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.util.io.PathSegmentUtil;

public final class ImportExportFileNameUtil {

    private ImportExportFileNameUtil() {
        // Utility class.
    }

    public static String createFilePrefix(final DocRef docRef) {
        // The type and uuid become part of a file name, so strip anything that could escape the segment.
        return (docRef.getName() != null
                ? toSafeFileName(docRef.getName(), 100) + "."
                : "") +
               PathSegmentUtil.cleanSegment(docRef.getType()) +
               "." +
               PathSegmentUtil.cleanSegment(docRef.getUuid());
    }

    public static String toSafeFileName(final String string, final int maxLength) {
        String safe = string.replaceAll("[^A-Za-z0-9]", "_");
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        return safe;
    }
}
