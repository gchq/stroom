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

import com.google.inject.ImplementedBy;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@ImplementedBy(SimplePathCreator.class)
public interface PathCreator {

    String replaceTimeVars(String path);

    String replaceTimeVars(String path, ZonedDateTime dateTime);

    String replaceSystemProperties(String path);

    /**
     * Turns an application relative path into an absolute path making use of the home directory location set for the
     * application and performing any other system property replacement that may be needed.
     */
    Path toAppPath(String pathString);

    String replaceUUIDVars(String path);

    String replaceFileName(String path, String fileName);

    String[] findVars(String path);

    boolean containsVars(String path);

    String replace(String path,
                   String var,
                   LongSupplier replacementSupplier,
                   int pad);

    /**
     * Replaces ALL instances of '{@code ${param}}' with the value supplied by replacementSupplier.
     *
     * @param str                 The {@link String} to replace params in.
     * @param var                 The param to replace (without the wrapping '{@code ${...}}'.
     * @param replacementSupplier Supplier of the replacement value.
     * @return str with all instances of param replaced.
     */
    String replace(String str,
                   String var,
                   Supplier<String> replacementSupplier);

    String replaceAll(String path);

    String replaceContextVars(String path);

    @Override
    String toString();
}
