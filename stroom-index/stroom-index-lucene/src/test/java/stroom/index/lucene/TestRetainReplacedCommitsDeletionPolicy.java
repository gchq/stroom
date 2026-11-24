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

package stroom.index.lucene;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests [RetainReplacedCommitsDeletionPolicy] against a real index, as what matters is which files Lucene
/// leaves on disk for a reader on another node.
class TestRetainReplacedCommitsDeletionPolicy {

    private static final long RETENTION_MS = 30 * 60 * 1000;

    private final AtomicLong clock = new AtomicLong(1_000_000);
    private int nextDocId;

    @Test
    void testDefaultPolicyDeletesTheFilesAReaderIsUsing(@TempDir final Path dir) throws IOException {
        // The problem being solved: without retention a merge and commit removes the files of the commit
        // that a reader on another node opened.
        try (final Directory directory = new NIOFSDirectory(dir);
                final IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            final Collection<String> filesTheReaderUses = commitTwoSegments(writer, directory);

            mergeAndCommit(writer);

            assertThat(filesTheReaderUses).anySatisfy(fileName -> assertThat(dir.resolve(fileName)).doesNotExist());
            assertThat(DirectoryReader.listCommits(directory)).hasSize(1);
        }
    }

    @Test
    void testReplacedCommitIsKeptForTheRetentionPeriod(@TempDir final Path dir) throws IOException {
        final RetainReplacedCommitsDeletionPolicy policy = newPolicy();
        try (final Directory directory = new NIOFSDirectory(dir);
                final IndexWriter writer = new IndexWriter(directory, newConfig(policy))) {
            final Collection<String> filesTheReaderUses = commitTwoSegments(writer, directory);
            final IndexCommit commitTheReaderUses = lastCommit(directory);

            mergeAndCommit(writer);

            // Both commits made so far have been replaced. Every file of the one the reader opened is still
            // there, so a reader that opened it can carry on, and
            // one that had only just read the commit point can still open it.
            assertThat(filesTheReaderUses).allSatisfy(fileName -> assertThat(dir.resolve(fileName)).exists());
            assertThat(policy.getRetainedCommitCount()).isEqualTo(2);
            try (final DirectoryReader reader = DirectoryReader.open(commitTheReaderUses)) {
                assertThat(reader.numDocs()).isEqualTo(2);
            }
            // A new reader gets the latest commit.
            try (final DirectoryReader reader = DirectoryReader.open(directory)) {
                assertThat(reader.numDocs()).isEqualTo(3);
            }

            // Still kept by a commit made just inside the retention period.
            clock.addAndGet(RETENTION_MS - 1);
            addDocAndCommit(writer);
            assertThat(filesTheReaderUses).allSatisfy(fileName -> assertThat(dir.resolve(fileName)).exists());
            assertThat(policy.getRetainedCommitCount()).isEqualTo(3);

            // Deleted by the first commit after the retention period, which keeps the one replaced a
            // moment ago and the one it has just replaced.
            clock.addAndGet(1);
            addDocAndCommit(writer);
            assertThat(filesTheReaderUses).anySatisfy(fileName -> assertThat(dir.resolve(fileName)).doesNotExist());
            assertThat(policy.getRetainedCommitCount()).isEqualTo(2);
            assertThat(DirectoryReader.listCommits(directory)).hasSize(3);
        }
    }

    @Test
    void testCommitsFoundOnOpeningAreKeptThenDeleted(@TempDir final Path dir) throws IOException {
        final Collection<String> filesTheReaderUses;
        try (final Directory directory = new NIOFSDirectory(dir);
                final IndexWriter writer = new IndexWriter(directory, newConfig(newPolicy()))) {
            filesTheReaderUses = commitTwoSegments(writer, directory);
            mergeAndCommit(writer);
        }

        // The writer is closed and opened again much later, perhaps by another process. When the commit was
        // replaced is no longer known, so it is kept as if it had only just been replaced.
        clock.addAndGet(10 * RETENTION_MS);
        final RetainReplacedCommitsDeletionPolicy policy = newPolicy();
        try (final Directory directory = new NIOFSDirectory(dir);
                final IndexWriter writer = new IndexWriter(directory, newConfig(policy))) {
            assertThat(policy.getRetainedCommitCount()).isEqualTo(2);
            assertThat(filesTheReaderUses).allSatisfy(fileName -> assertThat(dir.resolve(fileName)).exists());

            clock.addAndGet(RETENTION_MS);
            addDocAndCommit(writer);

            assertThat(filesTheReaderUses).anySatisfy(fileName -> assertThat(dir.resolve(fileName)).doesNotExist());
        }
    }

    @Test
    void testZeroRetentionKeepsOnlyTheLatestCommit(@TempDir final Path dir) throws IOException {
        final RetainReplacedCommitsDeletionPolicy policy =
                new RetainReplacedCommitsDeletionPolicy(0, clock::get);
        try (final Directory directory = new NIOFSDirectory(dir);
                final IndexWriter writer = new IndexWriter(directory, newConfig(policy))) {
            commitTwoSegments(writer, directory);
            mergeAndCommit(writer);

            assertThat(policy.getRetainedCommitCount()).isZero();
            assertThat(DirectoryReader.listCommits(directory)).hasSize(1);
        }
    }

    @Test
    void testTheLatestCommitIsNeverDeleted(@TempDir final Path dir) throws IOException {
        try (final Directory directory = new NIOFSDirectory(dir)) {
            try (final IndexWriter writer = new IndexWriter(directory, newConfig(newPolicy()))) {
                addDocAndCommit(writer);
            }
            // However old the only commit is, opening and closing a writer leaves it alone.
            clock.addAndGet(100 * RETENTION_MS);
            try (final IndexWriter writer = new IndexWriter(directory, newConfig(newPolicy()))) {
                assertThat(writer.getDocStats().numDocs).isEqualTo(1);
            }
            try (final DirectoryReader reader = DirectoryReader.open(directory)) {
                assertThat(reader.numDocs()).isEqualTo(1);
            }
        }
    }

    private RetainReplacedCommitsDeletionPolicy newPolicy() {
        return new RetainReplacedCommitsDeletionPolicy(RETENTION_MS, clock::get);
    }

    private IndexWriterConfig newConfig(final RetainReplacedCommitsDeletionPolicy policy) {
        return new IndexWriterConfig(new StandardAnalyzer()).setIndexDeletionPolicy(policy);
    }

    // Makes a commit of two segments and returns the files a reader of that commit depends on.
    private Collection<String> commitTwoSegments(final IndexWriter writer,
                                                 final Directory directory) throws IOException {
        addDocAndCommit(writer);
        addDocAndCommit(writer);
        final IndexCommit commit = lastCommit(directory);
        final Collection<String> fileNames = commit.getFileNames();
        assertThat(fileNames).isNotEmpty();
        return fileNames;
    }

    // Merging to one segment makes every earlier segment file redundant once the merge is committed.
    private void mergeAndCommit(final IndexWriter writer) throws IOException {
        addDoc(writer);
        writer.forceMerge(1);
        writer.commit();
    }

    private void addDocAndCommit(final IndexWriter writer) throws IOException {
        addDoc(writer);
        writer.commit();
    }

    private void addDoc(final IndexWriter writer) throws IOException {
        final Document document = new Document();
        document.add(new StringField("id", String.valueOf(nextDocId++), Store.YES));
        writer.addDocument(document);
    }

    private IndexCommit lastCommit(final Directory directory) throws IOException {
        final List<IndexCommit> commits = DirectoryReader.listCommits(directory);
        return commits.getLast();
    }

    // Guards against the test passing because the files were never there.
    @Test
    void testCommitFilesExistBeforeBeingReplaced(@TempDir final Path dir) throws IOException {
        try (final Directory directory = new NIOFSDirectory(dir);
                final IndexWriter writer = new IndexWriter(directory, newConfig(newPolicy()))) {
            final Collection<String> fileNames = commitTwoSegments(writer, directory);

            assertThat(fileNames).allSatisfy(fileName -> assertThat(Files.exists(dir.resolve(fileName))).isTrue());
        }
    }
}
