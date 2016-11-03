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

package stroom.test;

import java.io.File;
import java.io.IOException;

public final class ProjectPathUtil {
    public static File resolveDir(final String projectDir) {
        File dir;
        try {
            File root = new File(".").getCanonicalFile();
            dir = new File(root, projectDir);
            if (!dir.isDirectory()) {
                dir = new File(root.getParentFile(), projectDir);
                if (!dir.isDirectory()) {
                    throw new RuntimeException("Path not found: " + dir.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return dir;
    }
}
