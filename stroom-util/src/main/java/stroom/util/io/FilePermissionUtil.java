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

package stroom.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;

public class FilePermissionUtil {
    static final long MS_IN_DAY = 24L * 60L * 60L * 1000L;

    public static void tracePath(File file) throws IOException {
        Path path = file.toPath();
        BasicFileAttributes basicFileAttributes = Files.readAttributes(path, BasicFileAttributes.class);

        Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(path);

        System.out.println("File Attributes: " + file);
        System.out.println();
        System.out.println("createTime      " + basicFileAttributes.creationTime());
        System.out.println("lastAccessTime  " + basicFileAttributes.lastAccessTime());
        System.out.println("creationTime    " + basicFileAttributes.creationTime());
        System.out.println("filePermissions " + PosixFilePermissions.toString(filePermissions));
        System.out.println("canWrite        " + file.canWrite());
        System.out.println();

    }

    public static void main(String[] args) throws IOException {
        File file = new File(args[0]);
        Path path = file.toPath();

        tracePath(file);
        Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(path);

        System.out.println("Setting Permission ");
        System.out.println();

        Set<PosixFilePermission> newFilePermissions = new HashSet<>();
        for (PosixFilePermission filePermission : filePermissions) {
            if (filePermission.equals(PosixFilePermission.OWNER_WRITE)) {
                newFilePermissions.add(PosixFilePermission.OWNER_READ);
            } else if (filePermission.equals(PosixFilePermission.GROUP_WRITE)) {
                newFilePermissions.add(PosixFilePermission.GROUP_READ);
            } else if (filePermission.equals(PosixFilePermission.OTHERS_WRITE)) {
                newFilePermissions.add(PosixFilePermission.OTHERS_READ);
            } else {
                newFilePermissions.add(filePermission);
            }
        }
        if (!file.setReadOnly()) {
            System.out.println("Unable to set readonly");
        }

        FileTime newFileAccessTime = FileTime.fromMillis(
                Files.readAttributes(path, BasicFileAttributes.class).lastAccessTime().toMillis() + MS_IN_DAY);
        System.out.println("Setting lastAccessTime +24H");
        Files.setAttribute(path, "lastAccessTime", newFileAccessTime);

        System.out.println();

        tracePath(file);

    }
}
