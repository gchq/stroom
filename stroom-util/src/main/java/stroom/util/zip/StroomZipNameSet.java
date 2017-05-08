/*
 * Copyright 2017 Crown Copyright
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

import stroom.util.io.StreamUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class that you can feed a bunch of names and it will deduce if the
 * file type based on it's extension and the base name based on what other files
 * we have been sent in.
 *
 * It indexes the files various ways to speed up time it takes to figure out
 * matching files etc.
 */
public class StroomZipNameSet {
    public static final Set<String> CONTEXT_FILE_EXT = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList(".ctx", ".context")));
    public static final Set<String> META_FILE_EXT = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList(".hdr", ".header", ".meta", ".met")));
    public static final Set<String> MANIFEST_FILE_EXT = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList(".mf", ".manifest")));

    private static final Map<StroomZipFileType, Set<String>> FILE_EXT_MAP;

    static {
        HashMap<StroomZipFileType, Set<String>> map = new HashMap<StroomZipFileType, Set<String>>();
        map.put(StroomZipFileType.Context, CONTEXT_FILE_EXT);
        map.put(StroomZipFileType.Meta, META_FILE_EXT);
        map.put(StroomZipFileType.Manifest, MANIFEST_FILE_EXT);
        FILE_EXT_MAP = Collections.unmodifiableMap(map);

    }

    private final Map<StroomZipFileType, Map<String, String>> entryMap;
    private final Map<String, String> fileNameToBaseNameMap;

    private final Set<Integer> baseNameLengthSet;
    private final Set<String> baseNameSet;
    private final List<String> baseNameList;

    private final Set<String> unknownFileNameSet;
    private final List<String> unknownFileNameList;

    // Used to check the order
    private final List<String> baseNameCheckOrderList;
    private final Map<StroomZipFileType, Integer> baseNameCheckOrderListIndex;

    public StroomZipNameSet(boolean checkOrder) {
        entryMap = new HashMap<>();
        baseNameCheckOrderListIndex = new HashMap<>();
        Arrays.stream(StroomZipFileType.values())
              .forEach(fileType -> {
                  entryMap.put(fileType, new HashMap<>());
                  baseNameCheckOrderListIndex.put(fileType, 0);
              });
        baseNameLengthSet = new HashSet<>();
        baseNameSet = new HashSet<>();
        baseNameList = new ArrayList<>();
        unknownFileNameSet = new HashSet<>();
        unknownFileNameList = new ArrayList<>();

        fileNameToBaseNameMap = new HashMap<>();
        if (checkOrder) {
            baseNameCheckOrderList = new ArrayList<>();
        } else {
            baseNameCheckOrderList = null;
        }
    }

    public static StroomZipFileType getStroomZipFileType(final String fileName) {
        StroomZipFileType type = FILE_EXT_MAP.entrySet()
                                          .stream()
                                          .filter(entry -> looksLike(fileName, entry.getValue()) != null)
                                          .map(Entry::getKey)
                                          .findFirst()
                                          .orElse(StroomZipFileType.Data);
        return type;
    }

    private static String looksLike(final String fileName, Set<String> extSet) {
        String lowerFileName = fileName.toLowerCase(StreamUtil.DEFAULT_LOCALE);
        for (String ext : extSet) {
            if (lowerFileName.endsWith(ext)) {
                return fileName.substring(0, fileName.length() - ext.length());
            }
        }
        return null;
    }

    public void add(Collection<String> fileNameList) {
        for (String fileName : fileNameList) {
            add(fileName);
        }
    }

    public StroomZipEntry add(final String fileName) {
        String baseName = null;

        // Header or Context File ?
        for (StroomZipFileType stroomZipFileType : FILE_EXT_MAP.keySet()) {
            baseName = addToMapIfLooksLike(stroomZipFileType, fileName);
            if (baseName != null) {
                return new StroomZipEntry(fileName, baseName, stroomZipFileType);
            }
        }

        // Data File ... try and match it to a header or context to get the base
        // name
        for (int i = fileName.length(); i > 0; i--) {
            if (baseNameLengthSet.contains(i)) {
                baseName = fileName.substring(0, i);

                // We already have an entry for this file
                if (entryMap.get(StroomZipFileType.Data).containsKey(baseName)) {
                    break;
                }

                // Do we already know about this base name type
                if (baseNameSet.contains(baseName)) {
                    keyEntry(StroomZipFileType.Data, fileName, baseName);
                    return new StroomZipEntry(fileName, baseName, StroomZipFileType.Data);
                }
            }
        }
        // Did not find any matching context or header
        unknownFileNameSet.add(fileName);
        unknownFileNameList.add(fileName);
        return new StroomZipEntry(fileName, null, StroomZipFileType.Data);
    }

    private void checkBaseName(String baseName) {
        String bestMatch = null;

        for (String unknownFileName : unknownFileNameSet) {
            if (unknownFileName.startsWith(baseName)) {
                if (bestMatch == null) {
                    bestMatch = unknownFileName;
                } else {
                    if (unknownFileName.length() < bestMatch.length()) {
                        bestMatch = unknownFileName;
                    }
                }
            }
        }

        if (bestMatch != null) {
            if (unknownFileNameList != null) {
                Iterator<String> unknownFileNameItr = unknownFileNameList.iterator();

                while (unknownFileNameItr.hasNext()) {
                    String unknownFileName = unknownFileNameItr.next();
                    unknownFileNameItr.remove();
                    unknownFileNameSet.remove(unknownFileName);
                    if (unknownFileName.equals(bestMatch)) {
                        keyEntry(StroomZipFileType.Data, unknownFileName, baseName);
                        break;
                    } else {
                        keyEntry(StroomZipFileType.Data, unknownFileName, unknownFileName);
                    }
                }
            } else {
                unknownFileNameSet.remove(bestMatch);
                // key this pair
                keyEntry(StroomZipFileType.Data, bestMatch, baseName);
            }
        }
    }

    /**
     * @return all the valid base names as we know them
     */
    public Set<String> getBaseNameSet() {
        return Stream.concat(
                entryMap.get(StroomZipFileType.Data).keySet().stream(),
                unknownFileNameSet.stream())
                     .collect(Collectors.toSet());
    }

    /**
     * @return all the valid base names as we know them
     */
    public List<String> getBaseNameList() {
        return Stream.concat(
                baseNameList.stream(),
                unknownFileNameList.stream())
                     .collect(Collectors.toList());
    }


    /**
     * @return given a delimiter grouping "e.g. '_'" return back sets of base
     * names around that grouping e.g. 001_01.dat 001_01.meta 001_02.dat
     * 001_02.meta 002.dat 002.meta 003.dat 003.meta 004.dat 004.meta
     * <p>
     * would return a list {001_01, 001_02}, {002}, {003}, {004}
     */
    public List<List<String>> getBaseNameGroupedList(String grouping) {
        //TODO this method is not used by stroom-proxy or stroom-proxy-util but is used
        //by StreamUploadTaskHandler in Stroom

        List<List<String>> rtnList = new ArrayList<>();

        String currentGrouping = null;
        List<String> currentList = null;

        for (String baseName : getBaseNameList()) {
            int pos = baseName.lastIndexOf(grouping);
            if (pos != -1) {
                String newGrouping = baseName.substring(0, pos);
                if (!newGrouping.equals(currentGrouping)) {
                    currentGrouping = newGrouping;
                    currentList = new ArrayList<>();
                    rtnList.add(currentList);
                }
                currentList.add(baseName);
            } else {
                currentGrouping = baseName;
                currentList = new ArrayList<>();
                currentList.add(baseName);
                rtnList.add(currentList);
            }
        }

        return rtnList;
    }

    /**
     * @return given a base name return back the full file name
     */
    public String getName(String baseName, StroomZipFileType stroomZipFileType) {
        if (StroomZipFileType.Data.equals(stroomZipFileType)) {
            if (unknownFileNameSet.contains(baseName)) {
                return baseName;
            }
        }
        return entryMap.get(stroomZipFileType).get(baseName);
    }

    /**
     * @return given a file name return back the base name
     */
    public String getBaseName(String fileName) {
        String baseName = fileNameToBaseNameMap.get(fileName);
        if (baseName == null) {
            return fileName;
        }
        return baseName;

    }

    private String addToMapIfLooksLike(StroomZipFileType stroomZipFileType, final String fileName) {
        String baseName = looksLike(fileName, FILE_EXT_MAP.get(stroomZipFileType));

        if (baseName != null) {
            checkBaseName(baseName);
            keyEntry(stroomZipFileType, fileName, baseName);
            return baseName;
        }
        return null;
    }

    private void keyEntry(StroomZipFileType stroomZipFileType, final String fileName, String baseName) {
        if (fileNameToBaseNameMap.get(fileName) != null) {
            throw StroomZipNameException.createDuplicateFileNameException(fileName);
        }
        entryMap.get(stroomZipFileType).put(baseName, fileName);
        fileNameToBaseNameMap.put(fileName, baseName);
        baseNameLengthSet.add(baseName.length());
        if (baseNameSet.add(baseName)) {
            baseNameList.add(baseName);
        }

        if (baseNameCheckOrderList != null) {
            // Are we adding a new item to the baseNameCheckOrderList ?
            int idx = baseNameCheckOrderListIndex.get(stroomZipFileType);
            if (idx == baseNameCheckOrderList.size()) {
                if (baseNameCheckOrderList.contains(baseName)) {
                    throw StroomZipNameException.createOutOfOrderException(fileName);
                }
                baseNameCheckOrderList.add(baseName);
                baseNameCheckOrderListIndex.put(stroomZipFileType, idx + 1);
            } else {
                int baseNameIndex = baseNameCheckOrderList.indexOf(baseName);
                if (baseNameIndex == -1) {
                    throw StroomZipNameException.createOutOfOrderException(fileName);
                } else {
                    if (baseNameIndex < idx) {
                        throw StroomZipNameException.createOutOfOrderException(fileName);
                    } else {
                        baseNameCheckOrderListIndex.put(stroomZipFileType, baseNameIndex + 1);
                    }
                }
            }
        }
    }
}
