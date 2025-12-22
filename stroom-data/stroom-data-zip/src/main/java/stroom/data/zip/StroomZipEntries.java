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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

/**
 * This class is responsible for grouping file entries in a stroom format zip by their common base name.
 * It relies on the standard file extensions defined in {@link StroomZipFileType}.
 * Files with no extension or a non-standard extension are treated as data files.
 * There can be only one of each file type in a group, identified by its base name.
 * <pre>
 *  Filename        Group BaseName      Notes
 *  001             1     001           # No ext so treated as 001.dat
 *  001.ctx         1     001
 *
 *  002.dat         2     002
 *  002.ctx         2     002
 *
 *  003.unknown     3     003           # Non-standard ext, initially treated as 003.unknown.dat
 *  003.ctx         3     003           # At this point we re-classify the one above as 003.dat
 *
 *  abc.xyz.004     4     abc.xyz.004   # Non-standard ext, so treated as abc.xyz.004.dat
 *
 *  abc.xyz.005     5     abc.xyz.005   # Non-standard ext, so treated as abc.xyz.005.dat
 *
 *  abc.xyz.006     6     abc.xyz.006   # Non-standard ext, so treated as abc.xyz.006.dat
 *  abc.xyz.006.ctx 6     abc.xyz.006
 *
 *  007.foo         7     007           # Non-standard ext, so treated as 007.foo.dat
 *  007.bar         7     007           # Non-standard ext, so treated as 007.bar.dat
 *  007.ctx         7     007           # Now we re-classify the two above as 007.dat and 007.dat and
 *                                        raise dup file error
 *  </pre>
 */
public class StroomZipEntries {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomZipEntries.class);

    // baseName => StroomZipEntryGroup
    private final Map<String, StroomZipEntryGroup> map = new HashMap<>();
    // baseNames in the order they are seen in the zip
    private final List<String> baseNames = new ArrayList<>();
    // StroomZipEntry basename => FileName (the baseName in FileName may differ from the key)
    // e.g. 001.unknown => filename('001.unknown', '001', 'unknown')
    private final Map<String, FileName> unknownExtensionFileNames = new HashMap<>();

    public StroomZipEntry addFile(final String fileName) {
        // createFromFileName treats unknown extensions as part of the base name, e.g.
        // '001.unknown' has baseName '001.unknown' but
        // '001.meta' has baseName '001' as '.meta' is a known extension
        // If we see '001.unknown' then '001.meta' then we have two base names for the same group
        // so, we need to re-classify '001.unknown' from baseName '001.unknown' => '001'.
        // However we could receive files like:
        //   2023.11.15.10001
        //   2023.11.15.10002
        //   2023.11.15.10003
        // In which case we want the baseName to be the whole filename as 1000X is not an extension.
        StroomZipEntry stroomZipEntry = StroomZipEntry.createFromFileName(fileName);
        LOGGER.debug("addFile - fileName: '{}', stroomZipEntry: {}", fileName, stroomZipEntry);

        final String baseName = stroomZipEntry.getBaseName();

        if (!stroomZipEntry.hasKnownExtension()
            && stroomZipEntry.getFullName().contains(".")) {
            // e.g. some random extension '001.mydata'
            // but they may have '001.mydata' and '001.meta' which would have different base names
            // ('001.mydata' and '001') so we need to link them up

            // This parse knows nothing about known extension so just splits the filename up
            // to give us '001' as a basename
            final FileName fn = FileName.parse(stroomZipEntry.getFullName());
            if (map.containsKey(fn.getBaseName())) {
                // We already have a group for this baseName so assume that our belongs to that and
                // re-classify our stroomZipEntry with the existing baseName
                final StroomZipEntry newZipEntry = stroomZipEntry.cloneWithNewBaseName(fn.getBaseName());
                LOGGER.debug("Re-classifying {} as {}", stroomZipEntry, newZipEntry);
                stroomZipEntry = newZipEntry;
            } else {
                // No existing groups with e.g. '001' baseName so keep a record of it so, we can
                // deal with it if say a '001.meta' is added.
                LOGGER.debug("Found unknown extension, baseName: '{}', fn: {}", baseName, fn);
                unknownExtensionFileNames.put(baseName, fn);
            }
        } else {
            // A known extension or no extension at all, so check if its baseName is the stem or one in
            // unknownExtensionBaseNames
            String baseNameToRemove = null;
            boolean foundMatch = false;
            for (final Entry<String, FileName> entry : unknownExtensionFileNames.entrySet()) {
                final String zipEntryBaseName = entry.getKey();
                final FileName fn2 = entry.getValue();
                if (Objects.equals(baseName, fn2.getBaseName())) {
                    if (foundMatch) {
                        // Already have a data file with this base name
                        throw StroomZipNameException.createDuplicateFileNameException(fn2.getFullName());
                    }
                    foundMatch = true;
                    // Common base name, e.g. '001' in '001.ctx' and '001.mydata', so need to re-classify it
                    final int idx = baseNames.indexOf(zipEntryBaseName);
                    if (idx >= 0) {
                        // Replace the incorrect baseName, i.e. '001.mydata' => '001'
                        baseNames.set(idx, baseName);
                        final StroomZipEntryGroup zipEntryGroup = map.remove(zipEntryBaseName);
                        if (zipEntryGroup != null) {
                            final StroomZipEntryGroup newZipEntryGroup = zipEntryGroup.cloneWithNewBaseName(baseName);
                            LOGGER.debug("Cloning group, {} => {}", zipEntryGroup, newZipEntryGroup);
                            map.put(baseName, newZipEntryGroup);
                            baseNameToRemove = zipEntryBaseName;
                        }
                    }
                }
            }
            // We've dealt with this one so remove it
            NullSafe.consume(baseNameToRemove, unknownExtensionFileNames::remove);
        }

        addZipEntry(stroomZipEntry);
        return stroomZipEntry;
    }

    private void addZipEntry(final StroomZipEntry stroomZipEntry) {
        final String baseName = stroomZipEntry.getBaseName();
        LOGGER.debug("Adding stroomZipEntry {} to group", stroomZipEntry);
        map.computeIfAbsent(stroomZipEntry.getBaseName(), k -> {
            LOGGER.debug("Creating group with baseName '{}'", baseName);
            baseNames.add(k);
            return new StroomZipEntryGroup(k);
        }).add(stroomZipEntry);
    }

    public Optional<StroomZipEntry> getByType(final String baseName, final StroomZipFileType stroomZipFileType) {
        final StroomZipEntryGroup group = map.get(baseName);
        if (group == null) {
            return Optional.empty();
        }
        return group.getByType(stroomZipFileType);
    }

    public Collection<StroomZipEntryGroup> getGroups() {
        return map.values();
    }

    public List<String> getBaseNames() {
        return baseNames;
    }

    @Override
    public String toString() {
        return "StroomZipEntries{" +
               "map=" + map +
               ", baseNames=" + baseNames +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static class StroomZipEntryGroup {

        private final String baseName;
        private final StroomZipEntry[] entries = new StroomZipEntry[StroomZipFileType.values().length];

        public StroomZipEntryGroup(final String baseName) {
            this.baseName = baseName;
        }

        public void add(final StroomZipEntry entry) {
            final int index = entry.getStroomZipFileType().getIndex();
            final StroomZipEntry existing = entries[index];
            if (existing != null) {
                throw StroomZipNameException.createDuplicateFileNameException(entry.getFullName());
            }
            if (!Objects.equals(baseName, entry.getBaseName())) {
                // Shouldn't happen as we are building the groups
                throw new RuntimeException("Base name mismatch, '" + baseName + "' and '"
                                           + entry.getBaseName() + '"');
            }
            entries[index] = entry;
        }

        public Optional<StroomZipEntry> getByType(final StroomZipFileType type) {
            return Optional.ofNullable(entries[type.getIndex()]);
        }

        public String getBaseName() {
            return baseName;
        }

        boolean hasEntries() {
            return Arrays.stream(entries)
                    .anyMatch(Objects::nonNull);
        }

        StroomZipEntryGroup cloneWithNewBaseName(final String newBaseName) {
            Objects.requireNonNull(newBaseName);
            if (Objects.equals(newBaseName, baseName) || !hasEntries()) {
                return this;
            } else {
                if (!this.baseName.startsWith(newBaseName)) {
                    throw new RuntimeException(LogUtil.message("newBaseName '{}' is not a prefix of baseName '{}'",
                            newBaseName, baseName));
                }

                final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup(newBaseName);
                for (final StroomZipEntry oldEntry : this.entries) {
                    if (oldEntry != null) {
                        final StroomZipEntry newEntry = oldEntry.cloneWithNewBaseName(newBaseName);
                        zipEntryGroup.add(newEntry);
                    }
                }
                return zipEntryGroup;
            }
        }

        @Override
        public String toString() {
            return "StroomZipEntryGroup{" +
                   "baseName='" + baseName + '\'' +
                   ", entries=" + Arrays.toString(entries) +
                   '}';
        }
    }
}
