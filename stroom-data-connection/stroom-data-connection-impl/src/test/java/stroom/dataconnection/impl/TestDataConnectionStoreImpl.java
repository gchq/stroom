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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.AthenaConnectionSettings;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.ExcelSettings;
import stroom.dataconnection.shared.JdbcConnectionSettings;
import stroom.docref.DocRef;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.importexport.api.ImportExportDocument;
import stroom.security.mock.MockSecurityContext;
import stroom.util.io.ByteSize;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceKey;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDataConnectionStoreImpl {

    private static DataConnectionStoreImpl store() {
        return store(null);
    }

    private static DataConnectionStoreImpl store(final ByteSize maxStoredDataSize) {
        return store(maxStoredDataSize, Mockito.mock(ExcelUploads.class));
    }

    private static DataConnectionStoreImpl store(final ByteSize maxStoredDataSize,
                                                     final ExcelUploads excelUploads) {
        final MockSecurityContext securityContext = new MockSecurityContext();
        final DataConnectionConfig config = new DataConnectionConfig(
                List.of("^jdbc:mysql://reporting-db:3306/.*$"), null, null, null, null, null, null, null,
                List.of("^eu-west-2/stroom-.*$"), null, true, maxStoredDataSize, null);
        return new DataConnectionStoreImpl(
                new StoreFactoryImpl(new MemoryPersistence(), null, securityContext, null, () -> null),
                securityContext,
                new DataConnectionSerialiser(new Serialiser2FactoryImpl()),
                () -> config,
                excelUploads);
    }

    private static DataConnectionDoc withUrl(final DataConnectionDoc doc, final String url) {
        return doc.copy()
                .jdbc(JdbcConnectionSettings.builder()
                        .url(url)
                        .build())
                .build();
    }

    @Test
    void createdDocumentHasDefaults() {
        final DataConnectionStoreImpl store = store();

        final DocRef docRef = store.createDocument("HR");
        final DataConnectionDoc doc = store.readDocument(docRef);

        assertThat(doc.getType()).isEqualTo(DataConnectionDoc.TYPE);
        assertThat(doc.getName()).isEqualTo("HR");
        assertThat(doc.getSourceType()).isEqualTo(DataConnectionType.JDBC);
        assertThat(doc.getMaxRows()).isEqualTo(DataConnectionDoc.DEFAULT_MAX_ROWS);
        assertThat(store.list()).containsExactly(docRef);
    }

    @Test
    void allowedUrlIsSaved() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("HR"));

        store.writeDocument(withUrl(doc, "jdbc:mysql://reporting-db:3306/hr"));

        assertThat(store.readDocument(doc.asDocRef()).getJdbc().getUrl())
                .isEqualTo("jdbc:mysql://reporting-db:3306/hr");
    }

    @Test
    void urlNotAllowedIsRefusedOnSave() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("HR"));

        assertThatThrownBy(() -> store.writeDocument(withUrl(doc, "jdbc:mysql://elsewhere:3306/hr")))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("allowedUrlPatterns");
        assertThat(store.readDocument(doc.asDocRef()).getJdbc())
                .as("nothing was saved")
                .isNull();
    }

    @Test
    void deniedPropertyIsRefusedOnSave() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("HR"));

        assertThatThrownBy(() -> store.writeDocument(
                withUrl(doc, "jdbc:mysql://reporting-db:3306/hr?allowLoadLocalInfile=true")))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("allowLoadLocalInfile");
    }

    @Test
    void documentWithNoUrlYetCanBeSaved() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("HR"));

        store.writeDocument(doc.copy()
                .description("Not finished")
                .build());

        assertThat(store.readDocument(doc.asDocRef()).getDescription()).isEqualTo("Not finished");
    }

    @Test
    void athenaDocumentIgnoresLeftoverJdbcSettings() {
        // A JDBC URL left behind after switching the type is not used, so it is not checked.
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Events"));

        store.writeDocument(withUrl(doc, "jdbc:mysql://elsewhere:3306/hr").copy()
                .sourceType(DataConnectionType.ATHENA)
                .athena(AthenaConnectionSettings.builder()
                        .region("eu-west-2")
                        .workGroup("stroom-reports")
                        .build())
                .build());

        assertThat(store.readDocument(doc.asDocRef()).getSourceType()).isEqualTo(DataConnectionType.ATHENA);
    }

    @Test
    void athenaWorkGroupNotAllowedIsRefusedOnSave() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Events"));

        assertThatThrownBy(() -> store.writeDocument(doc.copy()
                .sourceType(DataConnectionType.ATHENA)
                .athena(AthenaConnectionSettings.builder()
                        .region("eu-west-2")
                        .workGroup("primary")
                        .build())
                .build()))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("allowedAthenaWorkGroups");
    }

    @Test
    void athenaDocumentWithNoRegionYetCanBeSaved() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Events"));

        store.writeDocument(doc.copy()
                .sourceType(DataConnectionType.ATHENA)
                .athena(AthenaConnectionSettings.builder()
                        .workGroup("anything")
                        .build())
                .build());

        assertThat(store.readDocument(doc.asDocRef()).getAthena().getWorkGroup()).isEqualTo("anything");
    }

    private static DataConnectionDoc withCsv(final DataConnectionDoc doc, final String data) {
        return doc.copy()
                .sourceType(DataConnectionType.CSV)
                .csv(CsvSettings.builder()
                        .columns(List.of(new DataColumn("name", DataColumnType.TEXT)))
                        .data(data)
                        .build())
                .build();
    }

    @Test
    void csvIsSavedAndReadBack() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Teams"));

        store.writeDocument(withCsv(doc, "name\nRed\nBlue\n"));

        final DataConnectionDoc read = store.readDocument(doc.asDocRef());
        assertThat(read.getCsv().getData()).isEqualTo("name\nRed\nBlue\n");
        assertThat(read.getCsv().getColumns()).containsExactly(new DataColumn("name", DataColumnType.TEXT));
    }

    @Test
    void csvIsStoredApartFromTheJson() throws Exception {
        // So it is kept and exported as plain CSV, and the JSON stays small.
        final DataConnectionSerialiser serialiser = new DataConnectionSerialiser(new Serialiser2FactoryImpl());
        final DataConnectionDoc doc = withCsv(DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("Teams")
                .build(), "name\nRed\n");

        final ImportExportDocument written = serialiser.write(doc);

        assertThat(new String(written.getExtAssetData(DataConnectionSerialiser.CSV), "UTF-8"))
                .isEqualTo("name\nRed\n");
        assertThat(serialiser.read(written)).isEqualTo(doc);
    }

    @Test
    void documentWithoutCsvHasNoCsvPart() throws Exception {
        final DataConnectionSerialiser serialiser = new DataConnectionSerialiser(new Serialiser2FactoryImpl());
        final DataConnectionDoc doc = DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("HR")
                .build();

        final ImportExportDocument written = serialiser.write(doc);

        assertThat(written.getExtAssetData(DataConnectionSerialiser.CSV)).isNull();
        assertThat(serialiser.read(written)).isEqualTo(doc);
    }

    @Test
    void csvOverTheLimitIsRefusedOnSave() {
        final DataConnectionStoreImpl store = store(ByteSize.ofBytes(10));
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Teams"));

        assertThatThrownBy(() -> store.writeDocument(withCsv(doc, "name\nRed\nBlue\n")))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("maxStoredDataSize");
        assertThat(store.readDocument(doc.asDocRef()).getCsv())
                .as("nothing was saved")
                .isNull();
    }

    @Test
    void csvOverTheLimitIsRefusedWhateverTheType() {
        // The CSV stays in the document after a switch to another type.
        final DataConnectionStoreImpl store = store(ByteSize.ofBytes(10));
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Teams"));

        assertThatThrownBy(() -> store.writeDocument(withCsv(doc, "name\nRed\nBlue\n").copy()
                .sourceType(DataConnectionType.JDBC)
                .build()))
                .hasMessageContaining("maxStoredDataSize");
    }

    @Test
    void delimiterOfMoreThanOneCharacterIsRefusedOnSave() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Teams"));
        final DataConnectionDoc withCsv = withCsv(doc, "name\nRed\n");

        assertThatThrownBy(() -> store.writeDocument(withCsv.copy()
                .csv(withCsv.getCsv().copy()
                        .delimiter("||")
                        .build())
                .build()))
                .hasMessageContaining("delimiter must be one character");
    }

    private static final byte[] WORKBOOK = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    private static final ResourceKey UPLOAD = new ResourceKey("upload-1", "teams.xlsx");

    private static DataConnectionDoc withExcel(final DataConnectionDoc doc, final ResourceKey uploadKey) {
        return doc.copy()
                .sourceType(DataConnectionType.EXCEL)
                .excel(ExcelSettings.builder()
                        .fileName("teams.xlsx")
                        .uploadKey(uploadKey)
                        .build())
                .build();
    }

    @Test
    void uploadedWorkbookIsStoredAndKeptOnLaterSaves() {
        final ExcelUploads uploads = Mockito.mock(ExcelUploads.class);
        Mockito.when(uploads.read(UPLOAD)).thenReturn(WORKBOOK);
        final DataConnectionStoreImpl store = store(null, uploads);
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Teams"));

        final DataConnectionDoc saved = store.writeDocument(withExcel(doc, UPLOAD));

        assertThat(store.readDocument(doc.asDocRef()).getExcel().getData()).isEqualTo(WORKBOOK);
        assertThat(store.readDocument(doc.asDocRef()).getExcel().getUploadKey())
                .as("the upload key is never stored")
                .isNull();
        Mockito.verify(uploads).delete(UPLOAD);

        // The editor sends the settings without the workbook, which it never has.
        store.writeDocument(withExcel(saved, null).copy()
                .excel(saved.getExcel().copy()
                        .data(null)
                        .sheet("Teams")
                        .build())
                .build());

        final DataConnectionDoc read = store.readDocument(doc.asDocRef());
        assertThat(read.getExcel().getData()).isEqualTo(WORKBOOK);
        assertThat(read.getExcel().getSheet()).isEqualTo("Teams");
    }

    @Test
    void workbookIsStoredApartFromTheJson() throws Exception {
        final DataConnectionSerialiser serialiser = new DataConnectionSerialiser(new Serialiser2FactoryImpl());
        final DataConnectionDoc doc = DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("Teams")
                .excel(ExcelSettings.builder()
                        .fileName("teams.xlsx")
                        .data(WORKBOOK)
                        .build())
                .build();

        final ImportExportDocument written = serialiser.write(doc);

        assertThat(written.getExtAssetData(DataConnectionSerialiser.XLSX)).isEqualTo(WORKBOOK);
        assertThat(serialiser.read(written)).isEqualTo(doc);
    }

    @Test
    void workbookOverTheLimitIsRefusedOnSave() {
        final ExcelUploads uploads = Mockito.mock(ExcelUploads.class);
        Mockito.when(uploads.read(UPLOAD)).thenReturn(WORKBOOK);
        final DataConnectionStoreImpl store = store(ByteSize.ofBytes(10), uploads);
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Teams"));

        assertThatThrownBy(() -> store.writeDocument(withExcel(doc, UPLOAD)))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("The workbook is");
        Mockito.verify(uploads, Mockito.never()).delete(Mockito.any());
    }

    @Test
    void savingACopyCopiesTheWorkbookOfTheDocumentItNames() {
        // Save As writes a new document, which has no workbook of its own yet.
        final ExcelUploads uploads = Mockito.mock(ExcelUploads.class);
        Mockito.when(uploads.read(UPLOAD)).thenReturn(WORKBOOK);
        final DataConnectionStoreImpl store = store(null, uploads);
        final DataConnectionDoc original = store.writeDocument(
                withExcel(store.readDocument(store.createDocument("Teams")), UPLOAD));
        final DataConnectionDoc copy = store.readDocument(store.createDocument("Teams copy"));

        store.writeDocument(copy.copy()
                .sourceType(DataConnectionType.EXCEL)
                .excel(original.getExcel().copy()
                        .data(null)
                        .copyWorkbookFrom(original.getUuid())
                        .build())
                .build());

        final DataConnectionDoc read = store.readDocument(copy.asDocRef());
        assertThat(read.getExcel().getData()).isEqualTo(WORKBOOK);
        assertThat(read.getExcel().getCopyWorkbookFrom()).isNull();
    }

    @Test
    void workbookIsRemovedWhenSavedWithNoFileName() {
        // The editor's Remove sends the settings with no file name.
        final ExcelUploads uploads = Mockito.mock(ExcelUploads.class);
        Mockito.when(uploads.read(UPLOAD)).thenReturn(WORKBOOK);
        final DataConnectionStoreImpl store = store(null, uploads);
        final DataConnectionDoc saved = store.writeDocument(
                withExcel(store.readDocument(store.createDocument("Teams")), UPLOAD));

        store.writeDocument(saved.copy()
                .excel(saved.getExcel().copy()
                        .fileName(null)
                        .data(null)
                        .build())
                .build());

        assertThat(store.readDocument(saved.asDocRef()).getExcel().getData()).isNull();
    }

    @Test
    void workbookOverTheLimitDoesNotStopAnotherTypeBeingSaved() {
        // Say a workbook was imported, which is not checked, and the type then changed.
        final DataConnectionStoreImpl store = store(ByteSize.ofBytes(10));
        final DataConnectionDoc doc = store.readDocument(store.createDocument("Teams"));
        final DataConnectionDoc withLargeWorkbook = doc.copy()
                .excel(ExcelSettings.builder()
                        .fileName("teams.xlsx")
                        .data(WORKBOOK)
                        .build())
                .build();

        store.writeDocument(withLargeWorkbook.copy()
                .sourceType(DataConnectionType.JDBC)
                .build());

        assertThatThrownBy(() -> store.writeDocument(store.readDocument(doc.asDocRef()).copy()
                .sourceType(DataConnectionType.EXCEL)
                .build()))
                .hasMessageContaining("The workbook is");
    }

    @Test
    void copyingFromAMissingDocumentIsRefused() {
        final DataConnectionStoreImpl store = store();
        final DataConnectionDoc copy = store.readDocument(store.createDocument("Teams copy"));

        assertThatThrownBy(() -> store.writeDocument(copy.copy()
                .sourceType(DataConnectionType.EXCEL)
                .excel(ExcelSettings.builder()
                        .fileName("teams.xlsx")
                        .copyWorkbookFrom("no-such-uuid")
                        .build())
                .build()))
                .hasMessageContaining("not found");
    }

    @Test
    void storedWorkbookAlwaysHasAFileNameAndNoUploadSettings() throws Exception {
        // A pack's JSON may lack the file name, which the editor sends back to keep the workbook.
        final DataConnectionSerialiser serialiser = new DataConnectionSerialiser(new Serialiser2FactoryImpl());
        final ImportExportDocument written = serialiser.write(DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("Teams")
                .excel(ExcelSettings.builder()
                        .uploadKey(UPLOAD)
                        .copyWorkbookFrom("uuid-2")
                        .data(WORKBOOK)
                        .build())
                .build());

        final ExcelSettings read = serialiser.read(written).getExcel();

        assertThat(read.getFileName()).isEqualTo(DataConnectionStoreImpl.DEFAULT_WORKBOOK_NAME);
        assertThat(read.getUploadKey()).isNull();
        assertThat(read.getCopyWorkbookFrom()).isNull();
    }
}
