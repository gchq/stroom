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

package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.util.io.FileName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * <p>
 * This class is used to ensure that zip files conform to the proxy zip standard.
 * </p><p>
 * The proxy file format consists of entries of 10 numeric characters and appropriate file extensions.
 * The entry order will also be specific with associated entries having the order (manifest, meta, context, data).
 * </p><p>
 * Note that it is not necessary for a manifest file (.mf) to exist at all but if must be the first file if it is
 * present. Meta files (.meta) must come before all other file types (except .mf).
 * It is also not necessary for context files (.ctx) to exist (in fact they are rarely used), but if present
 * they must follow the meta file (.meta). Finally all entry sets must include the actual data to be valid (.dat).
 * </p><p>
 * An example zip file will look like this:
 * 0000000001.mf
 * 0000000001.meta
 * 0000000001.ctx
 * 0000000001.dat
 * 0000000002.meta
 * 0000000002.ctx
 * 0000000002.dat
 * 0000000003.meta
 * 0000000003.dat
 * ...
 * </p>
 */
public class ProxyZipValidator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyZipValidator.class);

    private static final Set<StroomZipFileType> ALLOWED_BEFORE_META = EnumSet.of(
            StroomZipFileType.MANIFEST);
    private static final Set<StroomZipFileType> ALLOWED_BEFORE_CONTEXT = EnumSet.of(
            StroomZipFileType.MANIFEST,
            StroomZipFileType.META);
    private static final Set<StroomZipFileType> ALLOWED_BEFORE_DATA = EnumSet.of(
            StroomZipFileType.MANIFEST,
            StroomZipFileType.META,
            StroomZipFileType.CONTEXT);
    private static final Set<StroomZipFileType> REQUIRED_BEFORE_DATA = EnumSet.of(
            StroomZipFileType.META);

    private String lastBaseName;
    private StroomZipFileType lastType;
    private final Set<StroomZipFileType> lastTypes = EnumSet.noneOf(StroomZipFileType.class);
    private boolean valid = true;
    private long count;
    private String errorMessage;

    public void addEntry(final String entryName) {
        // Stop checking when entries are no longer valid.
        if (valid) {
            final FileName fileName = FileName.parse(entryName);
            final StroomZipFileType stroomZipFileType = StroomZipFileType.fromCanonicalExtension(
                    fileName.getExtension());

            if (stroomZipFileType == null) {
                error("An unknown entry type was found '" + entryName + "'");
            } else {
                if (!Objects.equals(lastBaseName, fileName.getBaseName())) {
                    if (lastBaseName != null && lastType != StroomZipFileType.DATA) {
                        error(LogUtil.message("Expected to have found {}.{} before {}",
                                lastBaseName, StroomZipFileType.DATA.getExtension(), entryName));
                    }
                    // If we are switching base name then check the base name is valid.
                    final String expectedBaseName = NumericFileNameUtil.create(++count);
                    if (!expectedBaseName.equals(fileName.getBaseName())) {
                        error("Unexpected base name found '" +
                              fileName.getBaseName() +
                              "' expected '" +
                              expectedBaseName +
                              "'");
                    } else {
                        // Set no previous extension as this is a new group.
                        lastTypes.clear();
                    }
                }

                // Check that the file type follows the expected order.
                checkExtensionOrder(entryName, stroomZipFileType, lastTypes);

                lastBaseName = fileName.getBaseName();
                lastType = stroomZipFileType;
                lastTypes.add(stroomZipFileType);
            }
        }
    }

    private void checkExtensionOrder(final String entryName,
                                     final StroomZipFileType type,
                                     final Set<StroomZipFileType> lastTypes) {
        // Check that the file type follows the expected order.
        switch (type) {
            case MANIFEST -> {
                if (lastBaseName != null) {
                    error("A manifest entry was found but was not the first item '" + entryName + "'");
                }
            }
            case META -> {
                checkAllowedBefore(entryName, type, lastTypes, ALLOWED_BEFORE_META);
            }
            case CONTEXT -> {
                checkAllowedBefore(entryName, type, lastTypes, ALLOWED_BEFORE_CONTEXT);
            }
            case DATA -> {
                checkAllowedBefore(entryName, type, lastTypes, ALLOWED_BEFORE_DATA);
                checkRequiredBefore(entryName, type, lastTypes, REQUIRED_BEFORE_DATA);
            }
        }
    }

    private void checkAllowedBefore(final String entryName,
                                    final StroomZipFileType type,
                                    final Set<StroomZipFileType> lastTypes,
                                    final Set<StroomZipFileType> allowedTypes) {
        // containsAll is fast, so do it first on the assumption that the zip is good
        if (!allowedTypes.containsAll(lastTypes)) {
            // One of lastTypes is not allowed so find out which
            for (final StroomZipFileType aLastType : lastTypes) {
                if (!allowedTypes.contains(aLastType)) {
                    error(LogUtil.message(
                            "An unexpected type {} was found before '{}'. Types allowed before {} are {}",
                            aLastType, entryName, type, allowedTypes));
                    break;
                }
            }
        }
    }

    private void checkRequiredBefore(final String entryName,
                                     final StroomZipFileType type,
                                     final Set<StroomZipFileType> lastTypes,
                                     final Set<StroomZipFileType> requiredTypes) {
        // containsAll is fast, so do it first on the assumption that the zip is good
        if (!lastTypes.containsAll(requiredTypes)) {
            // One of requiredTypes is not in lastTypes so find out which
            for (final StroomZipFileType aRequiredType : requiredTypes) {
                if (!lastTypes.contains(aRequiredType)) {
                    error(LogUtil.message(
                            "The type {} was not found before '{}'. Types required before {} are {}",
                            aRequiredType, entryName, type, requiredTypes));
                    break;
                }
            }
        }
    }

    private void finalCheck() {
        if (valid) {
            if (lastType == null) {
                error("No entries added");
            } else if (!StroomZipFileType.DATA.equals(lastType)) {
                error("The final entry was not a data entry '" + lastType + "'");
            }
        }
    }

    private void error(final String message) {
        valid = false;
        errorMessage = message;
        LOGGER.debug(errorMessage);
    }

    public boolean isValid() {
        finalCheck();
        return valid;
    }

    public String getErrorMessage() {
        finalCheck();
        return errorMessage;
    }
}
