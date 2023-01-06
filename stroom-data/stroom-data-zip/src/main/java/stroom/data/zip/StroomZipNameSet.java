package stroom.data.zip;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class that you can feed a bunch of names and it will deduce if the
 * file type based on it's extension and the base name based on what other files
 * we have been sent in.
 * <p>
 * It indexes the files various ways to speed up time it takes to figure out
 * matching files etc.
 */
public class StroomZipNameSet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomZipNameSet.class);

    private static final StroomZipFileType[] NON_DATA_TYPES = new StroomZipFileType[]{
            StroomZipFileType.MANIFEST,
            StroomZipFileType.META,
            StroomZipFileType.CONTEXT};

    // StroomZipFileType => (basename => filename)
    private final Map<StroomZipFileType, Map<String, String>> entryMap;
    private final Map<String, String> fileNameToBaseNameMap;

    // Holds the lengths of all the base names we have seen so far
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
        final String lowerFileName = fileName.toLowerCase(CharsetConstants.DEFAULT_LOCALE);
        for (final StroomZipFileType stroomZipFileType : NON_DATA_TYPES) {
            for (final String ext : stroomZipFileType.getRecognisedExtensions()) {
                if (lowerFileName.endsWith(ext)) {
                    return stroomZipFileType;
                }
            }
        }
        return StroomZipFileType.DATA;
    }

    private static String looksLike(final String fileName, final StroomZipFileType stroomZipFileType) {
        final String lowerFileName = fileName.toLowerCase(CharsetConstants.DEFAULT_LOCALE);
        for (final String ext : stroomZipFileType.getRecognisedExtensions()) {
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
        LOGGER.debug("add({})", fileName);
        String baseName;

        // Header or Context File ?
        for (StroomZipFileType stroomZipFileType : NON_DATA_TYPES) {
            baseName = addToMapIfLooksLike(stroomZipFileType, fileName);
            if (baseName != null) {
                LOGGER.debug("Creating StroomZipEntry for {}, {}, {}",
                        fileName, baseName, stroomZipFileType);
                return StroomZipEntry.create(fileName, baseName, stroomZipFileType);
            }
        }

        // Data File ... try and match it to a header or context to get the base
        // name
        for (int i = fileName.length(); i > 0; i--) {
            if (baseNameLengthSet.contains(i)) {
                baseName = fileName.substring(0, i);

                // We already have an entry for this file
                if (entryMap.get(StroomZipFileType.DATA).containsKey(baseName)) {
                    break;
                }

                // Do we already know about this base name type
                if (baseNameSet.contains(baseName)) {
                    final StroomZipFileType stroomZipFileType = StroomZipFileType.DATA;
                    keyEntry(stroomZipFileType, fileName, baseName);
                    LOGGER.debug("Creating StroomZipEntry for {}, {}, {}",
                            fileName, baseName, stroomZipFileType);
                    return StroomZipEntry.create(fileName, baseName, stroomZipFileType);
                }
            }
        }
        // Did not find any matching context or header
        unknownFileNameSet.add(fileName);
        unknownFileNameList.add(fileName);

        LOGGER.debug("Creating StroomZipEntry for {}, {}, {}",
                fileName, null, StroomZipFileType.DATA);
        return StroomZipEntry.create(fileName, null, StroomZipFileType.DATA);
    }

    private void checkBaseName(String baseName) {
        LOGGER.debug("checkBaseName({})", baseName);
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
                        keyEntry(StroomZipFileType.DATA, unknownFileName, baseName);
                        break;
                    } else {
                        keyEntry(StroomZipFileType.DATA, unknownFileName, unknownFileName);
                    }
                }
            } else {
                unknownFileNameSet.remove(bestMatch);
                // key this pair
                keyEntry(StroomZipFileType.DATA, bestMatch, baseName);
            }
        }
    }

    /**
     * @return all the valid base names as we know them
     */
    public Set<String> getBaseNameSet() {
        return Stream.concat(
                        entryMap.get(StroomZipFileType.DATA).keySet().stream(),
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
        // by StreamUploadTaskHandler in Stroom

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
        if (StroomZipFileType.DATA.equals(stroomZipFileType)) {
            if (unknownFileNameSet.contains(baseName)) {
                return baseName;
            }
        }
        return entryMap.get(stroomZipFileType)
                .get(baseName);
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

    private String addToMapIfLooksLike(final StroomZipFileType stroomZipFileType,
                                       final String fileName) {
        LOGGER.debug("addToMapIfLooksLike({}, {})", stroomZipFileType, fileName);
        final String baseName = looksLike(fileName, stroomZipFileType);

        if (baseName != null) {
            checkBaseName(baseName);
            keyEntry(stroomZipFileType, fileName, baseName);
            return baseName;
        }
        return null;
    }

    private void keyEntry(final StroomZipFileType stroomZipFileType,
                          final String fileName,
                          final String baseName) {
        LOGGER.debug("keyEntry({}, {}, {}", stroomZipFileType, fileName, baseName);

        if (fileNameToBaseNameMap.get(fileName) != null) {
            throw StroomZipNameException.createDuplicateFileNameException(fileName);
        }
        entryMap.get(stroomZipFileType)
                .put(baseName, fileName);
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
