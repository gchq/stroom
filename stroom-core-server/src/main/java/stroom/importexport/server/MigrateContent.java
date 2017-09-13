package stroom.importexport.server;/*
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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MigrateContent {
    // UUID structure 8-4-4-4-12
    private static final String UUID_REGEX = "[a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12}";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("You must supply a path that contains content to modify");
            System.exit(0);
        }

        final Path path = Paths.get(args[0]);
        if (!Files.isDirectory(path)) {
            System.err.println("'" + path.toAbsolutePath().toString() + "' is not a valid path");
            System.exit(0);
        }

        try (final Stream<Path> stream = Files.walk(path)) {
            final List<Path> list = stream
                    .filter(p -> {
                        final String fileName = p.getFileName().toString();
                        return fileName.matches(UUID_REGEX + "\\..*\\.node");
                    })
                    .collect(Collectors.toList());
            list.forEach(MigrateContent::process);
        } catch (final IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void process(final Path path) {
        try {
            System.out.println("Processing: " + path.toAbsolutePath().toString());

            final String fileName = path.getFileName().toString();
            String rem = fileName;

            String uuid = rem.substring(0, rem.indexOf("."));
            rem = rem.substring(uuid.length() + 1);
            String name = rem.substring(0, rem.indexOf("."));
            rem = rem.substring(name.length() + 1);
            String type = rem.substring(0, rem.indexOf("."));

            String origStem = uuid + "." + name + "." + type;
            String newStem = name + "." + type + "." + uuid;

            try (final Stream<Path> stream = Files.list(path.getParent())) {
                stream.filter(p -> p.getFileName().toString().startsWith(origStem))
                .forEach(p -> {
                    try {
                        String ext = p.getFileName().toString().substring(origStem.length());
                        String newFileName = newStem + ext;
                        Files.move(p, p.getParent().resolve(newFileName));
                    } catch (final IOException e) {
                        System.err.println(e.getMessage());
                    }
                });
            }

        } catch (final IOException e) {
            System.err.println(e.getMessage());
        }
    }
}