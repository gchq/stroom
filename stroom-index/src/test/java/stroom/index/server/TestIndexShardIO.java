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

package stroom.index.server;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import org.apache.lucene.document.Document;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardService;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFields;
import stroom.search.server.IndexShardSearcher;
import stroom.search.server.IndexShardSearcherImpl;
import stroom.node.shared.Volume;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestIndexShardIO extends StroomUnitTest {
    private static IndexShardService service = new MockIndexShardService();
    private static IndexFields indexFields = IndexFields.createStreamIndexFields();

    static {
        indexFields.add(IndexField.createField("Id"));
        indexFields.add(IndexField.createField("Test"));
        indexFields.add(IndexField.createField("Id2"));
    }

    private Document buildDocument(final int id) {
        final Document d = new Document();
        d.add(FieldFactory.create(IndexField.createIdField("Id"), id));
        d.add(FieldFactory.create(IndexField.createField("Test"), "Test"));
        d.add(FieldFactory.create(IndexField.createIdField("Id2"), id));

        return d;
    }

    @Test
    public void testOpenCloseManyWrite() throws IOException {
        final Volume volume = new Volume();
        volume.setPath(getCurrentTestDir().getAbsolutePath());
        final Index index = new Index();
        index.setName("Test");

        final IndexShard idx1 = new IndexShard();
        idx1.setId(1L);
        idx1.setIndex(index);
        idx1.setPartition("all");
        idx1.setVolume(volume);
        idx1.setIndexVersion(LuceneVersionUtil.getCurrentVersion());

        // Clean up from previous tests.
        final File dir = IndexShardUtil.getIndexDir(idx1);
        FileSystemUtil.deleteDirectory(dir);

        for (int i = 1; i <= 10; i++) {
            final boolean first = i == 1;
            final IndexShardWriter writer = new IndexShardWriterImpl(service, indexFields, index, idx1);
            writer.open(first);
            writer.flush();
            writer.addDocument(buildDocument(i));
            writer.flush();
            Assert.assertEquals(i, idx1.getDocumentCount());
            writer.close();
        }
    }

    @Test
    public void testOpenCloseManyReadWrite() throws IOException {
        final Index index = new Index();
        index.setName("Test");

        final Volume volume = new Volume();
        volume.setPath(getCurrentTestDir().getAbsolutePath());
        final IndexShard idx1 = new IndexShard();
        idx1.setIndex(index);
        idx1.setPartition("all");
        idx1.setId(1L);
        idx1.setVolume(volume);
        idx1.setIndexVersion(LuceneVersionUtil.getCurrentVersion());

        // Clean up from previous tests.
        final File dir = IndexShardUtil.getIndexDir(idx1);
        FileSystemUtil.deleteDirectory(dir);

        final IndexShardWriter writer = new IndexShardWriterImpl(service, indexFields, index, idx1);

        for (int i = 1; i <= 10; i++) {
            final boolean create = i == 1;
            writer.open(create);
            writer.addDocument(buildDocument(i));
            Assert.assertEquals(i - 1, idx1.getDocumentCount());
            writer.close();
            Assert.assertEquals(i, idx1.getDocumentCount());

            final IndexShardSearcher searcher = new IndexShardSearcherImpl(idx1);
            searcher.open();
            Assert.assertEquals(i, searcher.getReader().maxDoc());
            searcher.close();
        }
    }

    @Test
    public void testFailToCloseAndReopen() throws IOException {
        final Index index = new Index();
        index.setName("Test");

        final Volume volume = new Volume();
        volume.setPath(getCurrentTestDir().getAbsolutePath());
        final IndexShard idx1 = new IndexShard();
        idx1.setIndex(index);
        idx1.setPartition("all");
        idx1.setId(1L);
        idx1.setVolume(volume);
        idx1.setIndexVersion(LuceneVersionUtil.getCurrentVersion());

        // Clean up from previous tests.
        final File dir = IndexShardUtil.getIndexDir(idx1);
        FileSystemUtil.deleteDirectory(dir);

        final IndexShardWriter writer = new IndexShardWriterImpl(service, indexFields, index, idx1);
        writer.open(true);

        for (int i = 1; i <= 10; i++) {
            writer.addDocument(buildDocument(i));
            writer.flush();
            Assert.assertEquals(i, idx1.getDocumentCount());
        }

        writer.close();
    }

    @Test
    public void testFailToCloseFlushAndReopen() throws IOException {
        final Index index = new Index();
        index.setName("Test");

        final Volume volume = new Volume();
        volume.setPath(getCurrentTestDir().getAbsolutePath());
        final IndexShard idx1 = new IndexShard();
        idx1.setIndex(index);
        idx1.setPartition("all");
        idx1.setId(1L);
        idx1.setVolume(volume);
        idx1.setIndexVersion(LuceneVersionUtil.getCurrentVersion());

        // Clean up from previous tests.
        final File dir = IndexShardUtil.getIndexDir(idx1);
        FileSystemUtil.deleteDirectory(dir);

        final IndexShardWriter writer = new IndexShardWriterImpl(service, indexFields, index, idx1);
        writer.open(true);

        for (int i = 1; i <= 10; i++) {
            writer.addDocument(buildDocument(i));
            Assert.assertEquals("No docs flushed ", i - 1, idx1.getDocumentCount());
            writer.flush();
            Assert.assertEquals("No docs flushed ", i, idx1.getDocumentCount());
        }

        writer.close();
    }

    @Test
    public void testWriteLoadsNoFlush() throws IOException {
        final Index index = new Index();
        index.setName("Test");

        final Volume volume = new Volume();
        final File testDir = getCurrentTestDir();
        volume.setPath(testDir.getAbsolutePath());
        FileSystemUtil.deleteDirectory(testDir);
        final IndexShard idx1 = new IndexShard();
        idx1.setIndex(index);
        idx1.setPartition("all");
        idx1.setId(1L);
        idx1.setVolume(volume);
        idx1.setIndexVersion(LuceneVersionUtil.getCurrentVersion());

        // Clean up from previous tests.
        final File dir = IndexShardUtil.getIndexDir(idx1);
        FileSystemUtil.deleteDirectory(dir);

        final IndexShardWriterImpl writer = new IndexShardWriterImpl(service, indexFields, index, idx1);
        writer.open(true);

        Long lastSize = null;

        final HashSet<Integer> flushSet = new HashSet<Integer>();

        for (int i = 1; i <= 100; i++) {
            writer.addDocument(buildDocument(i));
            writer.sync();
            // System.out.println(writer.getIndexWriter().ramSizeInBytes());

            final Long newSize = idx1.getFileSize();

            if (newSize != null) {
                if (lastSize != null) {
                    if (!lastSize.equals(newSize)) {
                        flushSet.add(Integer.valueOf(i));
                    }
                }
                lastSize = newSize;
            }

        }
        // TODO - TO Fix
        // Assert.assertEquals("Some flush happened before we expected it "
        // + flushSet, 0, flushSet.size());

        writer.close();
        Assert.assertTrue("Expected not to flush", flushSet.isEmpty());
        // Assert.assertEquals("Expected to flush every 2048 docs...","[2048,
        // 6144, 4096, 8192]",
        // flushSet.toString());
    }

    public static void main(final String[] args) {
        for (final Object s : System.getProperties().keySet()) {
            System.out.println(s + "=" + System.getProperty((String) s));
        }
    }
}
