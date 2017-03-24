/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.zip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Utility to build a path for a given id in such a way that we don't exceed OS
 * dir file limits.
 *
 * So file 1 is 001 and file 1000 is 001/000 etc...
 */
public final class StroomFileNameUtil {

    public final static int MAX_FILENAME_LENGTH = 255;
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomFileNameUtil.class);

    private StroomFileNameUtil() {
        //static util methods only
    }

    public static String getDirPathForId(long id) {
        return buildPath(id, true);
    }

    /**
     * Build a file path for a id.
     */
    public static String getFilePathForId(long id) {
        return buildPath(id, false);
    }

    private static String buildPath(long id, boolean justDir) {
        StringBuilder fileBuffer = new StringBuilder();
        fileBuffer.append(id);
        // Pad out e.g. 10100 -> 010100
        while ((fileBuffer.length() % 3) != 0) {
            fileBuffer.insert(0, '0');
        }
        StringBuilder dirBuffer = new StringBuilder();
        for (int i = 0; i < fileBuffer.length() - 3; i += 3) {
            dirBuffer.append(fileBuffer.subSequence(i, i + 3));
            dirBuffer.append("/");
        }

        if (justDir) {
            return dirBuffer.toString();
        } else {
            return dirBuffer.toString() + fileBuffer.toString();
        }
    }

    public static String constructFilename(final String delimiter, long id, String... fileExtensions) {
        return constructFilename(delimiter, id, null, null, fileExtensions);

    }

    public static String constructFilename(final String delimiter, long id, final String filenameTemplate,
                                           final HeaderMap headerMap,
                                           String... fileExtensions) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Using delimiter [" + delimiter + "], filenameTemplate [" + filenameTemplate + "] and fileExtensions [" + Arrays.toString(fileExtensions) + "]");
        }

        final StringBuilder filenameBuilder = new StringBuilder();
        String idStr = getFilePathForId(id);
        filenameBuilder.append(idStr);

        int extensionsLength = 0;
        StringBuilder extensions = new StringBuilder();
        if (fileExtensions != null) {
            for (String extension : fileExtensions) {
                if (extension != null) {
                    extensions.append(extension);
                    extensionsLength += extension.length();
                }
            }
        }

        if (filenameTemplate != null && !filenameTemplate.isEmpty()) {
            String zipFilenameDelimiter = delimiter == null ? "" : delimiter;
            int lengthAvailableForTemplatedPart = MAX_FILENAME_LENGTH - idStr.length() - extensionsLength;
            filenameBuilder.append(zipFilenameDelimiter);
            String expandedTemplate = PathCreator.replace(filenameTemplate, headerMap, lengthAvailableForTemplatedPart);
            filenameBuilder.append(expandedTemplate);
        }

        filenameBuilder.append(extensions.toString());
        String filename = filenameBuilder.toString();
        return filename;
    }

}
