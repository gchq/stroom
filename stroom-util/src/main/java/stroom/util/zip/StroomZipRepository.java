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

import stroom.util.config.StroomProperties;
import stroom.util.date.DateUtil;
import stroom.util.io.FileSystemIterator;
import stroom.util.io.FileUtil;
import stroom.util.logging.StroomLogger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Class that represents a repository on the file system. By default files are
 * created in this repo using the stroom id structure where 1000 files are stored
 * per dir and dir's are created by padding the id to multiplier of 3 and using
 * each 3 part as a dir separator.
 */
public class StroomZipRepository {
    public final static String LOCK_EXTENSION = ".lock";
    public final static String ZIP_EXTENSION = ".zip";
    public final static String ERROR_EXTENSION = ".err";
    public final static String BAD_EXTENSION = ".bad";
    public final static String DEFAULT_ZIP_FILENAME_DELIMITER = "%";
    public final static String[] INVALID_ZIP_FILENAME_DELIMITERS = {"/", "\\", "$", "{", "}"};
    // 1 hour
    public final static int DEFAULT_LOCK_AGE_MS = 1000 * 60 * 60;
    public final static int MAX_FILENAME_LENGTH = 255;

    private final static StroomLogger LOGGER = StroomLogger.getLogger(StroomZipRepository.class);
    private final static Pattern ZIP_PATTERN = Pattern.compile(".*\\.zip");
    private final static Pattern ZIP_EXTENSION_PATTERN = Pattern.compile("\\.zip(\\.lock)?$");

    //TODO the regex is a bit simple as the non-id part could include numbers as well so
    private final static Pattern BASE_FILENAME_PATTERN = Pattern.compile("(\\d{3})+.*");
    /**
     * Date the repository was created
     */
    private final Date createDate;
    private final AtomicLong fileCount = new AtomicLong(0);
    private final AtomicBoolean finish = new AtomicBoolean(false);
    private final int lockDeleteAgeMs;
    private final String zipFilenameDelimiter;
    private final Pattern templatePartPattern;
    /**
     * Name of the repository while open
     */
    private File baseLockDir;

    /**
     * Final name once finished (may be null)
     */
    private File baseResultantDir;

    //TODO may be used by Stroom
    public StroomZipRepository(final String dir) {
        this(dir, false, DEFAULT_LOCK_AGE_MS, StroomProperties.getProperty("stroom.proxy.zipFilenameDelimiter", DEFAULT_ZIP_FILENAME_DELIMITER));
    }

    /**
     * Open a repository (with or without locking).
     */
    public StroomZipRepository(final String dir, final boolean lock, final int lockDeleteAgeMs,
                               final String zipFilenameDelimiter) {
        this.lockDeleteAgeMs = lockDeleteAgeMs;
        if (!isDelimiterValid(zipFilenameDelimiter)){
            LOGGER.error("zipFilenameDelimiter property [%s] is invalid, using the default [%s] instead",
                         zipFilenameDelimiter,
                         DEFAULT_ZIP_FILENAME_DELIMITER);
            this.zipFilenameDelimiter = DEFAULT_ZIP_FILENAME_DELIMITER;
        } else {
            this.zipFilenameDelimiter = zipFilenameDelimiter == null ? DEFAULT_ZIP_FILENAME_DELIMITER : zipFilenameDelimiter;
        }

        if (this.zipFilenameDelimiter != null && !this.zipFilenameDelimiter.isEmpty()) {
            templatePartPattern = Pattern.compile( Pattern.quote(this.zipFilenameDelimiter) + ".*" );
        } else {
            templatePartPattern = null;
        }

        createDate = new Date();
        if (lock) {
            baseLockDir = new File(dir + LOCK_EXTENSION);
            baseResultantDir = new File(dir);
            if (baseResultantDir.isDirectory()) {
                throw new RuntimeException("Rolled directory already exists " + baseResultantDir);
            }
        } else {
            baseLockDir = new File(dir);
        }

        // Create the root directory
        if (!baseLockDir.isDirectory() && !baseLockDir.mkdirs()) {
            throw new RuntimeException("Unable to create directory " + baseLockDir);
        }

        // We may be an existing repository so check for the last ID.
        final Long lastId = getLastFileId();
        if (lastId != null) {
            fileCount.set(lastId);
        }

        LOGGER.debug("() - Opened REPO %s lastId = %s", baseLockDir, lastId);
    }

    /**
     * @return last sequence or count in this repository.
     */
    public long getFileCount() {
        return fileCount.get();
    }

    /**
     * @param newCount new higher sequencer (used during testing)
     */
    public synchronized void setCount(final long newCount) {
        if (fileCount.get() > newCount) {
            throw new IllegalArgumentException("Can't reduce the size of count");
        }
        fileCount.set(newCount);
    }

    /**
     * The first is found matching on the shortest directory that contains a
     * file.
     *
     * @return get the first id
     */
    public Long getFirstFileId() {
        return scanForMatch(baseLockDir, false);
    }

    /**
     * @return get the last id
     */
    public Long getLastFileId() {
        return scanForMatch(baseLockDir, true);
    }

    /**
     * Scan for a match low or high
     */
    private Long scanForMatch(final File dir, final boolean last) {
        final List<String> fileList = new ArrayList<>();
        final List<String> dirList = new ArrayList<>();
        buildFileLists(dir, fileList, dirList);

        Long bestMatchHere = null;
        if (fileList.size() > 0) {
            if (last) {
                // Pick the last file
                bestMatchHere = Long.valueOf(fileList.get(fileList.size() - 1));
            } else {
                // Pick the first file
                bestMatchHere = Long.valueOf(fileList.get(0));
            }
        }

        // If not last return first file match
        if (!last && bestMatchHere != null) {
            return bestMatchHere;
        }

        // Otherwise get the best match from the sub directories.
        for (final String subDir : dirList) {
            final File subDirFile = new File(dir, subDir);
            final Long subDirBestMatch = scanForMatch(subDirFile, last);

            if (subDirBestMatch != null) {
                if (bestMatchHere == null) {
                    bestMatchHere = subDirBestMatch;
                } else {
                    if (last) {
                        if (subDirBestMatch.longValue() > bestMatchHere.longValue()) {
                            bestMatchHere = subDirBestMatch;
                        }
                    } else {
                        if (subDirBestMatch.longValue() < bestMatchHere.longValue()) {
                            bestMatchHere = subDirBestMatch;
                        }
                    }
                }
            }
        }
        return bestMatchHere;
    }

    /**
     * Build a list of valid file types. The list must contain files (just the
     * base name) and not be locked e.g. "001", "100111", for "001.zip",
     * "100111.zip" etc. "100112.zip.lock" would be ignored. And directories
     * that are using our standard e.g. "001", "002" etc.
     */
    private void buildFileLists(final File dir, final List<String> fileList, final List<String> dirList) {
        final String[] childFileArray = dir.list();

        // No Kids? exit
        if (childFileArray == null) {
            return;
        }

        for (String kidFileName : childFileArray) {
            // Is it a directory?
            // A small performance fix has been added here to only test sub dirs
            // that are 3 chars long as that's what the repo expects.
            if (kidFileName.length() == 3 && new File(dir, kidFileName).isDirectory()) {
                if (kidFileName.length() == 3) {
                    try {
                        Integer.parseInt(kidFileName);
                        dirList.add(kidFileName);
                    } catch (final Exception ex) {
                        LOGGER.warn("Directory " + dir + " contains invalid directory " + kidFileName
                                    + " that is not 3 digits!");
                    }
                }
            } else {
                //remove the zip extension
                String baseFilename = ZIP_EXTENSION_PATTERN.matcher(kidFileName).replaceFirst("");
                //remove the templated part if there is one
                if (templatePartPattern != null) {
                    baseFilename = templatePartPattern.matcher(baseFilename).replaceFirst("");
                }
                if (BASE_FILENAME_PATTERN.matcher(baseFilename).matches()) {
                    fileList.add(baseFilename);
                } else {
                    LOGGER.warn("File base name is not a valid repository file " + baseFilename);
                }
            }
        }

        Collections.sort(dirList);
        Collections.sort(fileList);
    }

    public Date getCreateDate() {
        return new Date(createDate.getTime());
    }

    public File getRootDir() {
        if (baseResultantDir != null) {
            return baseResultantDir;
        }
        return baseLockDir;
    }

    public synchronized void finish() {
        if (!finish.get()) {
            finish.set(true);
            removeLock();
        }
    }

    public StroomZipOutputStream getStroomZipOutputStream() throws IOException {
        return getStroomZipOutputStream(null, null);
    }

    public StroomZipOutputStream getStroomZipOutputStream(final HeaderMap headerMap, final String filenameTemplate)
            throws IOException {
        if (finish.get()) {
            throw new RuntimeException("No longer allowed to write new streams to a finished repository");
        }
        final String filename = StroomFileNameUtil.constructFilename(zipFilenameDelimiter, fileCount.incrementAndGet(),
                                                                  filenameTemplate, headerMap,
                                                                  ZIP_EXTENSION);
        final File file = new File(baseLockDir, filename);
        // Ensure parent dir's exist
        FileUtil.mkdirs(file.getParentFile());
        return new StroomZipOutputStream(file);
    }


    private final String constructFilenameGlob(long id, String... fileExtensions) {
        final StringBuilder filenameBuilder = new StringBuilder();
        filenameBuilder.append(StroomFileNameUtil.getFilePathForId(id));
        filenameBuilder.append("*");
        if (fileExtensions != null) {
            for (String extension : fileExtensions) {
                filenameBuilder.append(extension);
            }
        }
        return filenameBuilder.toString();
    }

    private File getErrorFile(final StroomZipFile zipFile) {
        final String path = zipFile.getFile().getAbsolutePath();
        if (path.endsWith(BAD_EXTENSION)) {
            return new File(path.substring(0, path.length() - ZIP_EXTENSION.length() - BAD_EXTENSION.length())
                            + ERROR_EXTENSION + BAD_EXTENSION);
        } else {
            return new File(path.substring(0, path.length() - ZIP_EXTENSION.length()) + ERROR_EXTENSION);
        }
    }

    @SuppressWarnings(value = "DM_DEFAULT_ENCODING")
    public void addErrorMessage(final StroomZipFile zipFile, final String msg, final boolean bad) {
        try {
            File errorFile = getErrorFile(zipFile);
            if (!zipFile.getFile().isFile()) {
                return;
            }

            if (bad) {
                final File renamedFile = new File(zipFile.getFile().getAbsolutePath() + BAD_EXTENSION);
                if (!zipFile.renameTo(renamedFile)) {
                    LOGGER.warn("Failed to rename zip file to " + renamedFile);
                }
                if (errorFile.isFile()) {
                    final File renamedErrorFile = new File(errorFile.getAbsolutePath() + BAD_EXTENSION);
                    if (errorFile.renameTo(renamedErrorFile)) {
                        errorFile = renamedErrorFile;
                    }
                }
            }

            final PrintWriter pw = new PrintWriter(errorFile);
            pw.println(msg);
            pw.close();
        } catch (final IOException ex) {
            LOGGER.warn("Failed to write to file " + zipFile + " message " + msg);
        }
    }

    public boolean isBad(final long id) {
        //TODO do we really need to build the full filename, can we just do a regex match instead
        return isFileOfType(id, ZIP_EXTENSION, BAD_EXTENSION);
    }

    public boolean isError(final long id) {
        if (isBad(id)) {
            //TODO do we really need ot build the full filename, can we just do a regex match instead
            return isFileOfType(id, ERROR_EXTENSION, BAD_EXTENSION);
        }
        //TODO do we really need ot build the full filename, can we just do a regex match instead
        return isFileOfType(id, ERROR_EXTENSION);
    }

    public boolean isFile(final long id) {
        return isFileOfType(id, ZIP_EXTENSION);
    }

    public boolean isFileOfType(final long id, String... extensions) {
        //TODO do we really need ot build the full filename, can we just do a regex match instead
        String filenameGlob = constructFilenameGlob(id, extensions);
        try {
            Iterator<Path> iterator = Files.newDirectoryStream(baseLockDir.toPath(), filenameGlob).iterator();
            if (iterator.hasNext()) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error finding files with glob " + filenameGlob);
        }
    }

//    public ZipInputStream getZipInputStream(final long id) throws IOException {
//        final File file = new File(baseLockDir, StroomFileNameUtil.getFilePathForId(id) + ZIP_EXTENSION);
//        if (file.isFile()) {
//            return new ZipInputStream(new FileInputStream(file));
//        }
//        return null;
//    }

//    public StroomZipFile getZipFile(final long id) throws IOException {
//        final File file = new File(baseLockDir, StroomFileNameUtil.getFilePathForId(id) + ZIP_EXTENSION);
//        if (file.isFile()) {
//            return new StroomZipFile(file);
//        }
//        return null;
//    }

    public void clean() {
        LOGGER.debug("clean() " + baseLockDir);
        clean(baseLockDir);
    }

    private void clean(final File root) {
        final List<String> fileList = new ArrayList<String>();
        final List<String> dirList = new ArrayList<String>();
        buildFileLists(root, fileList, dirList);

        for (final String subDir : dirList) {
            clean(new File(root, subDir));
        }

        for (final String file : fileList) {
            final File lockFile = new File(root, file + ZIP_EXTENSION + LOCK_EXTENSION);
            if (lockFile.isFile()) {
                final long oldestTimeMs = System.currentTimeMillis() - lockDeleteAgeMs;
                final long lastModMs = lockFile.lastModified();
                if (lastModMs < oldestTimeMs) {
                    if (lockFile.delete()) {
                        LOGGER.info("clean() - Removed old lock file due to age " + lockFile + " "
                                    + DateUtil.createNormalDateTimeString());
                    } else {
                        LOGGER.error("clean() - Unable to remove old lock file dur to age " + lockFile);
                    }
                }
            }
        }

        deleteDirIfNotActive(root);
    }

    private void removeLock() {
        if (baseResultantDir != null) {
            if (!baseLockDir.renameTo(baseResultantDir)) {
                throw new RuntimeException("Unable to rename dircetory " + baseLockDir + " to " + baseResultantDir);
            }
            baseResultantDir = null;
            // No-longer locked
            baseLockDir = baseResultantDir;
        }
    }

    public boolean deleteIfEmpty() {
        if (deleteEmptyDir(baseLockDir)) {
            LOGGER.debug("deleteIfEmpty() - Removed " + baseLockDir);
            return true;
        }
        return false;
    }

    private boolean deleteEmptyDir(final File root) {
        // Dir Gone !
        if (!root.isDirectory()) {
            return true;
        }
        final String[] kids = root.list();

        for (final String kid : kids) {
            final File kidFile = new File(root, kid);
            if (kidFile.isDirectory()) {
                if (!deleteEmptyDir(kidFile)) {
                    // Failed to prune a kid dir.
                    return false;
                }
            } else {
                // Some files are left.
                return false;
            }
        }

        return root.delete();
    }

    public void delete(final StroomZipFile zipFile) {
        try {
            // Delete the file.
            final File errorfile = getErrorFile(zipFile);
            zipFile.delete();
            if (errorfile.isFile()) {
                FileUtil.deleteFile(errorfile);
            }
        } catch (final IOException ioEx) {
            LOGGER.error("delete() - Unable to delete zip file " + zipFile.getFile(), ioEx);
        }
    }

    private void deleteDirIfNotActive(final File dir) {
        final String currentDir = StroomFileNameUtil.getDirPathForId(getFileCount());
        final File activeDir = new File(baseLockDir, currentDir);

        if (!dir.equals(activeDir)) {
            deleteIfEmpty(dir);
        }
    }

    private boolean isDelimiterValid(final String delimiter) {
        if (delimiter != null && !delimiter.isEmpty()) {
            for (String invalidDelimiter : INVALID_ZIP_FILENAME_DELIMITERS) {
                if (delimiter.contains(invalidDelimiter)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean deleteIfEmpty(final File dir) {
        boolean deleted = false;
        if (dir.isDirectory()) {
            final String[] kids = dir.list();
            if (kids.length == 0) {
                final String repoDirPath = baseLockDir.getAbsolutePath();
                final String thisDirPath = dir.getAbsolutePath();

                if (thisDirPath.length() > repoDirPath.length()) {
                    deleted = true;
                    FileUtil.deleteDir(dir);
                    deleteIfEmpty(dir.getParentFile());
                }
            }
        }
        return deleted;
    }

    public Iterable<File> getZipFiles() {
        final File rootDir = getRootDir();
        if (rootDir != null && rootDir.isDirectory()) {
            return () -> new FileSystemIterator(rootDir, ZIP_PATTERN);
        } else {
            LOGGER.error("getZipFiles() - root dir %s is not a directory !", rootDir);
            return new ArrayList<>();
        }

    }
}
