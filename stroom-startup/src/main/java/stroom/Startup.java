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

package stroom;

import stroom.util.io.FileUtil;
import com.google.gwt.dev.DevMode;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Startup extends StartupHelper {
    public static final String PROJECT_DIR = "stroom-startup";
    public static final String JAVA_SOURCE_DIR = "src/main/java";
    public static final String RESOURCES_DIR = "src/main/resources";

    public static void main(final String[] args) throws Exception {
        final String workingPath = System.getProperty("user.dir");
        final Path tempDir = Paths.get(workingPath).resolve(".temp");
        if (!Files.isDirectory(tempDir)) {
            Files.createDirectories(tempDir);
        }

        System.out.println("Setting temp dir to: " + tempDir.toAbsolutePath().toString());
        System.setProperty("java.io.tmpdir", tempDir.toAbsolutePath().toString());

        addSourcesToClasspath();
        FileUtil.useDevTempDir();

        fixArgs(args, tempDir.resolve("gwt-DevMode"), true);
        System.out.println("Starting DevMode with arguments: ");
        for (String arg : args){
            System.out.println("  " + arg);
        }
        DevMode.main(args);
    }

    private static void addSourcesToClasspath() {
        final String workingPath = System.getProperty("user.dir");
        final File workingDir = new File(workingPath);
        File projectDir = new File(workingDir, PROJECT_DIR);

        if (!projectDir.isDirectory()) {
            projectDir = new File(workingDir.getParentFile(), PROJECT_DIR);
            if (!projectDir.isDirectory()) {
                System.err.println(projectDir.getAbsolutePath() + " cannot be found");
                return;
            }
        }

        final File rootDir = projectDir.getParentFile();
        for (final File dir : rootDir.listFiles()) {
            if (dir.isDirectory()) {
                final File javaDir = new File(dir, JAVA_SOURCE_DIR);
                if (javaDir.isDirectory()) {
                    addDir(javaDir);
                }

                final File resourcesDir = new File(dir, RESOURCES_DIR);
                if (resourcesDir.isDirectory()) {
                    addDir(resourcesDir);
                }
            }
        }
    }

    private static void addDir(File dir) {
        try {
            addURL(dir.toURI().toURL());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addURL(URL u) {
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class sysclass = URLClassLoader.class;

        try {
            Method method = sysclass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(sysloader, u);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error, could not add URL to system classloader");
        }
    }
}
