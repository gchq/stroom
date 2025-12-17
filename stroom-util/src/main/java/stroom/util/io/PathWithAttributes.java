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

package stroom.util.io;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Wraps a {@link Path} and its {@link BasicFileAttributes}.
 * The {@link BasicFileAttributes} are static and correct at the time
 * they were created.
 *
 * @param path
 * @param attributes
 */
public record PathWithAttributes(Path path, BasicFileAttributes attributes) {

    public PathWithAttributes {
        Objects.requireNonNull(attributes);
    }

    /**
     * @see BasicFileAttributes#lastModifiedTime()
     */
    public FileTime lastModifiedTime() {
        return attributes.lastModifiedTime();
    }

    /**
     * @see BasicFileAttributes#lastAccessTime()
     */
    public FileTime lastAccessTime() {
        return attributes.lastAccessTime();
    }

    /**
     * @see BasicFileAttributes#creationTime()
     */
    public FileTime creationTime() {
        return attributes.creationTime();
    }

    /**
     * @see BasicFileAttributes#isRegularFile()
     */
    public boolean isRegularFile() {
        return attributes.isRegularFile();
    }

    /**
     * @see BasicFileAttributes#isDirectory()
     */
    public boolean isDirectory() {
        return attributes.isDirectory();
    }

    /**
     * @see BasicFileAttributes#isSymbolicLink()
     */
    public boolean isSymbolicLink() {
        return attributes.isSymbolicLink();
    }

    /**
     * @see BasicFileAttributes#isOther()
     */
    public boolean isOther() {
        return attributes.isOther();
    }

    /**
     * @see BasicFileAttributes#size()
     */
    public long size() {
        return attributes.size();
    }

    /**
     * @see BasicFileAttributes#fileKey()
     */
    public Object fileKey() {
        return attributes.fileKey();
    }

    @Override
    public String toString() {
        final List<String> attrs = new ArrayList<>();
        if (isRegularFile()) {
            attrs.add("file");
        }
        if (isDirectory()) {
            attrs.add("dir");
        }
        if (isOther()) {
            attrs.add("other");
        }
        if (isSymbolicLink()) {
            attrs.add("symlink");
        }
        return path
               + " (" + String.join(", ", attrs)
               + "), size: " + ByteSize.ofBytes(size())
               + ", created: " + attributes.creationTime();
    }
}
