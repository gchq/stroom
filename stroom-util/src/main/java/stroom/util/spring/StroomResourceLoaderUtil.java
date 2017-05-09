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

package stroom.util.spring;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class StroomResourceLoaderUtil {
//    private static String configReplace = null;
//    private static final String TEST_CONFIG_PATH = "stroom-config";
//
//    static {
//        try {
//            File parent = new File(".");
//            parent = new File(parent.getCanonicalPath());
//
//            // Go back up through parents to try and find the config directory.
//            File configDir = null;
//            while (parent != null && (configDir == null || !configDir.isDirectory())) {
//                configDir = new File(parent, TEST_CONFIG_PATH);
//                parent = parent.getParentFile();
//            }
//
//            if (configDir != null && configDir.isDirectory()) {
//                configReplace = "file://" + configDir.getCanonicalPath() + "/";
//            }
//        } catch (final Exception e) {
//            e.printStackTrace();
//        }
//
//    }

    public static Resource getResource(ResourceLoader resourceLoader, String path) {
//        if (configReplace != null) {
//            String newPath = path.replace("classpath:", configReplace);
//            Resource resource = resourceLoader.getResource(newPath);
//            if (resource.exists()) {
//                return resource;
//            }
//        }
        return resourceLoader.getResource(path);
    }
}
