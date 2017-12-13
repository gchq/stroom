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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class GWTModuleCreator {
    private static final String MODULE_EXTENSION = ".gwt.xml";

    public static void main(final String[] args) {
        new GWTModuleCreator().generate();
    }

    private void generate() {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(
                "<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 2.1.1//EN\" \"http://google-web-toolkit.googlecode.com/svn/tags/2.1.1/distro-source/core/src/gwt-module.dtd\">\n");
        sb.append("<module>\n");
        sb.append("  <inherits name=\"com.google.gwt.user.User\" />\n");
        sb.append("\n");
        sb.append("  <!-- Import Stroom widgets -->\n");

        final Path rootDir = Paths.get(System.getProperty("user.home") + "/workspace/trunk/stroom-ui-mvp/src");
        final List<String> modules = new ArrayList<>();
        processDir(rootDir, rootDir.toAbsolutePath().normalize().toString(), modules);
        Collections.sort(modules);

        for (final String module : modules) {
            sb.append("  <inherits name=\"");
            sb.append(module);
            sb.append("\" />\n");
        }

        sb.append("</module>");
    }

    private void processDir(final Path dir, final String rootPath, final List<String> modules) {
        try (final Stream<Path> stream = Files.walk(dir)) {
            stream
                    .filter(p -> p.getFileName().toString().endsWith(MODULE_EXTENSION))
                    .forEach(p -> {
                        String module = p.toAbsolutePath().normalize().toString();
                        module = module.substring(rootPath.length() + 1);
                        module = module.substring(0, module.length() - MODULE_EXTENSION.length());
                        module = module.replaceAll("/", ".");
                        modules.add(module);
                    });

        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
