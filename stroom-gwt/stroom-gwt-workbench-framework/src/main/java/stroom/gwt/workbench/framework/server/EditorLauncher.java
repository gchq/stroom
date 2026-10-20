/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// Opens a file in the user's editor, for 'Open in editor', the equivalent of React Storybook's
/// launch-editor.
///
/// The editor is the command in the `STROOM_WORKBENCH_EDITOR` environment variable (e.g. `idea` or
/// `code`), or failing that IntelliJ IDEA (`idea`) or VS Code (`code`) if on the path.
public class EditorLauncher {

    /// The environment variable that holds the editor command.
    public static final String EDITOR_ENV_VAR = "STROOM_WORKBENCH_EDITOR";
    private static final List<String> KNOWN_EDITORS = List.of("idea", "code");
    private static final String DEFAULT_PATHEXT = ".COM;.EXE;.BAT;.CMD";

    private final Map<String, String> environment;
    private final boolean windows;

    /// @param environment The environment variables, e.g. `System.getenv()`.
    public EditorLauncher(final Map<String, String> environment) {
        this(environment, System.getProperty("os.name", "").startsWith("Windows"));
    }

    /// @param environment The environment variables, e.g. `System.getenv()`.
    /// @param windows     True if running on Windows, where the path is separated by `;` and
    ///                    executables are found using the extensions in `PATHEXT`.
    EditorLauncher(final Map<String, String> environment, final boolean windows) {
        this.environment = environment;
        this.windows = windows;
    }

    /// Opens a file at a line.
    ///
    /// @param file The file.
    /// @param line The line number (1 based).
    /// @return True if an editor was started, false if there is no editor.
    /// @throws IOException If the editor couldn't be started.
    public boolean open(final Path file, final int line) throws IOException {
        final Optional<List<String>> command = buildCommand(file, line);
        if (command.isEmpty()) {
            return false;
        }
        new ProcessBuilder(command.get())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        return true;
    }

    /// Builds the command that opens a file at a line.
    ///
    /// @param file The file.
    /// @param line The line number (1 based).
    /// @return The command that opens the file at the line, or empty if there is no editor.
    Optional<List<String>> buildCommand(final Path file, final int line) {
        final String configured = environment.get(EDITOR_ENV_VAR);
        final List<String> editor;
        if (configured != null && !configured.isBlank()) {
            editor = splitCommand(configured);
        } else {
            editor = KNOWN_EDITORS.stream()
                    .map(this::findOnPath)
                    .flatMap(Optional::stream)
                    .findFirst()
                    .map(name -> new ArrayList<>(List.of(name)))
                    .orElse(null);
        }
        if (editor == null || editor.isEmpty() || editor.getFirst().isBlank()) {
            return Optional.empty();
        }

        final String executable = Path.of(editor.get(0)).getFileName().toString();
        if (executable.startsWith("idea")) {
            editor.addAll(List.of("--line", String.valueOf(line), file.toString()));
        } else if (executable.startsWith("code")) {
            editor.addAll(List.of("-g", file + ":" + line));
        } else {
            editor.add(file.toString());
        }
        return Optional.of(editor);
    }

    /// Splits a command into its words at whitespace. Double quotes group words, e.g. to give a
    /// path containing spaces, and are removed.
    ///
    /// @param command The command, e.g. `"C:\Program Files\Editor\editor.exe" --new-window`.
    /// @return The words.
    static List<String> splitCommand(final String command) {
        final List<String> words = new ArrayList<>();
        final StringBuilder word = new StringBuilder();
        boolean inQuotes = false;
        boolean inWord = false;
        for (final char c : command.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                // Even an empty quoted string is a word
                inWord = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (inWord) {
                    words.add(word.toString());
                    word.setLength(0);
                    inWord = false;
                }
            } else {
                word.append(c);
                inWord = true;
            }
        }
        if (inWord) {
            words.add(word.toString());
        }
        return words;
    }

    /// @return The name to run the executable by if it is on the path. On Windows this includes
    /// the extension, e.g. `code.cmd`, as Java can't run a `.cmd` file without it.
    private Optional<String> findOnPath(final String executable) {
        final String path = environment.get("PATH");
        if (path == null) {
            return Optional.empty();
        }
        final List<String> names = windows
                ? executableNamesOnWindows(executable)
                : List.of(executable);
        final String separator = windows
                ? ";"
                : File.pathSeparator;
        for (final String dir : path.split(Pattern.quote(separator))) {
            if (dir.isBlank()) {
                continue;
            }
            for (final String name : names) {
                final Path file = Path.of(dir, name);
                // Windows has no execute permission so any file will do
                if (windows ? Files.isRegularFile(file) : Files.isExecutable(file)) {
                    return Optional.of(name);
                }
            }
        }
        return Optional.empty();
    }

    private List<String> executableNamesOnWindows(final String executable) {
        final String pathExt = environment.getOrDefault("PATHEXT", DEFAULT_PATHEXT);
        final List<String> names = new ArrayList<>();
        for (final String extension : pathExt.split(";")) {
            if (!extension.isBlank()) {
                names.add(executable + extension.trim().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
}
