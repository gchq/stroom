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

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public abstract class ExtensionFileVisitor extends AbstractFileVisitor {

    private final String extension;

    public ExtensionFileVisitor(final String extension) {
        this.extension = extension;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
        if (file != null && file.toString().endsWith(extension)) {
            return matchingFile(file, attrs);
        }
        return FileVisitResult.CONTINUE;
    }

    protected abstract FileVisitResult matchingFile(final Path file, final BasicFileAttributes attrs);
}
