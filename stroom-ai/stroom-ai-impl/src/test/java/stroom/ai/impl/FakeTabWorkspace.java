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

package stroom.ai.impl;

import stroom.ai.impl.AiWorkspaceDao.FileInfo;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceFileUnavailableException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAttachmentStatus;

import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/// A chat's workspace held in memory, behind mocks of [ChatWorkspace], [WorkspaceStore] and [AiWorkspaceDao], for
/// testing what keeps and reads the tab the user attached and snapshots of its results. A file can be made
/// unavailable, as when the user loses access to the document its content came from.
public final class FakeTabWorkspace {

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final WorkspaceStore workspaceStore = Mockito.mock(WorkspaceStore.class);
    private final AiWorkspaceDao workspaceDao = Mockito.mock(AiWorkspaceDao.class);
    private final Map<Integer, String> paths = new LinkedHashMap<>();
    private final Map<Integer, String> contents = new LinkedHashMap<>();
    private final Map<Integer, String> sources = new LinkedHashMap<>();
    private final Set<Integer> unavailable = new HashSet<>();
    private final Path dir;
    private int nextId = 100;

    /// @param dir A folder for local copies of files.
    @SuppressWarnings("unchecked")
    public FakeTabWorkspace(final Path dir) {
        this.dir = dir;
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(workspace.writeTabFile(Mockito.anyInt(), Mockito.any(WorkspacePath.class),
                Mockito.anyString(), Mockito.any())).thenAnswer(invocation -> write(
                ((WorkspacePath) invocation.getArgument(1)).path(), invocation.getArgument(2),
                invocation.getArgument(3)));
        Mockito.when(workspace.writeTabFile(Mockito.anyInt(), Mockito.any(WorkspacePath.class),
                Mockito.any(Path.class), Mockito.any())).thenAnswer(invocation -> write(
                ((WorkspacePath) invocation.getArgument(1)).path(),
                Files.readString(invocation.getArgument(2), StandardCharsets.UTF_8), invocation.getArgument(3)));
        Mockito.when(workspace.find(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenAnswer(invocation ->
                find(((WorkspacePath) invocation.getArgument(1)).path()));
        Mockito.when(workspace.localFile(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenAnswer(invocation ->
                localFile(find(((WorkspacePath) invocation.getArgument(1)).path()).orElseThrow()));
        Mockito.when(workspace.sourceDocUuid(Mockito.anyInt(), Mockito.any(WorkspacePath.class)))
                .thenAnswer(invocation -> sources.get(find(((WorkspacePath) invocation.getArgument(1)).path())
                        .orElseThrow()));
        Mockito.when(workspace.tempFile()).thenAnswer(invocation -> Files.createTempFile(dir, "tool", ".tmp"));
        Mockito.when(workspaceStore.localFile(Mockito.anyInt())).thenAnswer(invocation ->
                localFile(invocation.getArgument(0)));
        Mockito.when(workspaceDao.listFiles(Mockito.anyInt())).thenAnswer(invocation -> listFiles());
        Mockito.when(workspaceDao.getFileInfo(Mockito.anyInt())).thenAnswer(invocation ->
                Optional.ofNullable(info(invocation.getArgument(0))));
    }

    public ChatWorkspace workspace() {
        return workspace;
    }

    public WorkspaceStore workspaceStore() {
        return workspaceStore;
    }

    public AiWorkspaceDao workspaceDao() {
        return workspaceDao;
    }

    /// @return The content of the file at the path, if there is one.
    public Optional<String> content(final String path) {
        return find(path).map(contents::get);
    }

    /// @return The UUID of the document the file at the path came from, if there is a file there.
    public Optional<String> source(final String path) {
        return find(path).map(sources::get);
    }

    /// @return The paths of the files, in the order they were first written.
    public List<String> paths() {
        return new ArrayList<>(paths.values());
    }

    /// Makes every file unavailable, as when the user loses access to what they came from.
    public void makeAllUnavailable() {
        unavailable.addAll(paths.keySet());
    }

    private int write(final String path, final String content, final String source) {
        final int id = find(path).orElseGet(() -> nextId++);
        paths.put(id, path);
        contents.put(id, content);
        sources.put(id, source);
        unavailable.remove(id);
        return id;
    }

    private Optional<Integer> find(final String path) {
        return paths.entrySet().stream()
                .filter(entry -> entry.getValue().equals(path))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private Path localFile(final int id) {
        if (unavailable.contains(id)) {
            throw new WorkspaceFileUnavailableException(id, paths.get(id), "you no longer have access");
        }
        try {
            final Path file = dir.resolve("file-" + id);
            Files.writeString(file, contents.get(id), StandardCharsets.UTF_8);
            return file;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<FileInfo> listFiles() {
        return paths.keySet().stream().map(this::info).toList();
    }

    private FileInfo info(final int id) {
        final String path = paths.get(id);
        if (path == null) {
            return null;
        }
        return new FileInfo(id, 1, path, unavailable.contains(id)
                ? AiAttachmentStatus.UNAVAILABLE
                : AiAttachmentStatus.READY, sources.get(id), null, null,
                contents.get(id).getBytes(StandardCharsets.UTF_8).length);
    }
}
