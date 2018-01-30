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

import java.io.File;
import java.nio.file.Path;

import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.io.FileUtil;

public class StartupHelper {
    public static void setPath(final String[] args, final String match, final File file) {
        for (int i = 0; i < args.length - 1; i++) {
            final String key = args[i];
            final String value = args[i + 1];

            if (key.equals(match) && value.equalsIgnoreCase(".")) {
                FileUtil.mkdirs(file);
                String path = file.getAbsolutePath();
                try {
                    path = file.getCanonicalPath();
                } catch (final Exception e) {
                    System.err.println(e.getMessage());
                }

                args[i + 1] = path;
                // System.out.println("Setting arg" + match + " " + path);

                break;
            }
        }
    }

    public static void setPort(final String[] args, final String match, final String portEnd) {
        for (int i = 0; i < args.length - 1; i++) {
            final String key = args[i];
            final String value = args[i + 1];

            if (key.equals(match) && value.contains("$")) {
                args[i + 1] = value.replaceAll("\\$", portEnd);
                // System.out.println("Setting arg" + match + " " + args[i +
                // 1]);

                break;
            }
        }
    }

    public static void fixArgs(final String[] args, final Path tmpPath, final boolean clean) {
        final String userName = System.getProperty("user.name");
        final String workingPath = System.getProperty("user.dir");
        final File workingDir = new File(workingPath);
        File projectDir = new File(workingDir, "stroom-app-client");

        if (!projectDir.isDirectory()) {
            projectDir = new File(workingDir.getParentFile(), "stroom-app-client");
            if (!projectDir.isDirectory()) {
                System.err.println(projectDir.getAbsolutePath() + " cannot be found");
                return;
            }
        }

        // Resolve where the WAR file stuff is
        File runFile = new File(projectDir, "src/main/webapp");

        if (!runFile.isDirectory()) {
            runFile = new File(projectDir, "war");
            if (!runFile.isDirectory()) {
                System.err.println(runFile.getAbsolutePath()
                        + " is not the location of the WAR contents.  Is the working directory correctly set to ${workspace_loc:stroom-ui}?");
                return;
            }
        }

        FileUtil.mkdirs(tmpPath.toFile());

        if (clean) {
            System.out.println("Cleaning " + tmpPath.toAbsolutePath().toString());
            FileSystemUtil.deleteContents(tmpPath.toFile());
        }

        final String portEnd = String.valueOf(Math.abs(userName.hashCode() % 100));
        setPort(args, "-port", portEnd);
        setPort(args, "-codeServerPort", portEnd);

        String codeServerPort = System.getProperty("gwt.codeserver.port");
        if (codeServerPort != null && codeServerPort.length() > 0) {
            codeServerPort = codeServerPort.replaceAll("\\$", portEnd);
            System.setProperty("gwt.codeserver.port", codeServerPort);
        }

        setPath(args, "-war", runFile);
        setPath(args, "-logdir", tmpPath.resolve("log").toFile());
        setPath(args, "-gen", tmpPath.resolve("gen").toFile());
        setPath(args, "-extra", tmpPath.resolve("extra").toFile());
        setPath(args, "-workDir", tmpPath.resolve("work").toFile());

        final StringBuilder sb = new StringBuilder();
        for (final String arg : args) {
            sb.append(arg);
            sb.append(" ");
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        System.out.println(sb.toString());

        for (final String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("gwt.")) {
                final String value = System.getProperty(key);
                sb.setLength(0);
                sb.append(key);
                if (value.length() > 0) {
                    sb.append("=");
                    sb.append(value);
                }
                System.out.println(sb.toString());
            }
        }
    }
}
