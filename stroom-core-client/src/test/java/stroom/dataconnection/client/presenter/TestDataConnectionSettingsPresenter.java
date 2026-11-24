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

package stroom.dataconnection.client.presenter;

import stroom.dataconnection.client.presenter.DataConnectionSettingsPresenter.DataConnectionSettingsView;
import stroom.dataconnection.shared.AthenaConnectionSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.ExcelSettings;
import stroom.dataconnection.shared.JdbcConnectionSettings;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataConnectionSettingsPresenter {

    private DataConnectionSettingsPresenter presenter;
    private DataConnectionSettingsView view;

    @BeforeEach
    void setUp() {
        // The presenter's REST resource is made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();

        // The presenter's constructor builds GWT widgets, which only works in a browser, so mock
        // it and call the real method under test. The mock has read no document.
        presenter = Mockito.mock(DataConnectionSettingsPresenter.class);
        Mockito.when(presenter.writeExcel(Mockito.any())).thenCallRealMethod();

        view = Mockito.mock(DataConnectionSettingsView.class);
        Mockito.when(view.getExcelColumns()).thenReturn(List.of());
        Mockito.when(view.isExcelHeader()).thenReturn(true);
        Mockito.when(presenter.getView()).thenReturn(view);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void testChoosingExcelForDocumentThatNeverHadAWorkbook() {
        // gh-6025: this threw a NullPointerException.
        Mockito.when(view.getSourceType()).thenReturn(DataConnectionType.EXCEL);

        final ExcelSettings excel = presenter.writeExcel(DataConnectionDoc.builder().uuid("new").build());

        assertThat(excel).isNotNull();
        assertThat(excel.getFileName()).isNull();
        assertThat(excel.getSheet()).isNull();
        assertThat(excel.getHeader()).isNull();
        // Unset values stay unset while the editor shows its defaults.
        assertThat(excel.getColumns()).isNull();
        assertThat(excel.getUploadKey()).isNull();
        assertThat(excel.getCopyWorkbookFrom()).isNull();
    }

    @Test
    void testNoExcelSettingsForAnotherTypeThatNeverHadAny() {
        Mockito.when(view.getSourceType()).thenReturn(DataConnectionType.CSV);

        final ExcelSettings excel = presenter.writeExcel(DataConnectionDoc.builder().uuid("new").build());

        assertThat(excel).isNull();
    }

    @Test
    void testNoJdbcOrAthenaSettingsForAnotherTypeThatNeverHadAny() {
        // A CSV document read into the editor, which shows the JDBC and Athena defaults, then written
        // back, must be the same, or it is marked as changed.
        Mockito.when(view.getSourceType()).thenReturn(DataConnectionType.CSV);
        showJdbcAndAthenaDefaults();

        assertThat(DataConnectionSettingsPresenter.writeJdbc(view, null, null)).isNull();
        assertThat(DataConnectionSettingsPresenter.writeAthena(view, null, null)).isNull();
    }

    @Test
    void testJdbcAndAthenaDefaultsStayUnset() {
        Mockito.when(view.getSourceType()).thenReturn(DataConnectionType.JDBC);
        showJdbcAndAthenaDefaults();
        Mockito.when(view.getJdbcUrl()).thenReturn("jdbc:h2:mem:test");

        final JdbcConnectionSettings jdbc = DataConnectionSettingsPresenter.writeJdbc(
                view, null, JdbcConnectionSettings.builder().url("jdbc:h2:mem:test").build());
        final AthenaConnectionSettings athena = DataConnectionSettingsPresenter.writeAthena(
                view, null, AthenaConnectionSettings.builder().build());

        assertThat(jdbc).isEqualTo(JdbcConnectionSettings.builder().url("jdbc:h2:mem:test").build());
        assertThat(athena).isEqualTo(AthenaConnectionSettings.builder().build());
    }

    @Test
    void testJdbcAndAthenaValuesSetAreKept() {
        Mockito.when(view.getSourceType()).thenReturn(DataConnectionType.ATHENA);
        showJdbcAndAthenaDefaults();
        final JdbcConnectionSettings storedJdbc = JdbcConnectionSettings.builder()
                .maxPoolSize(JdbcConnectionSettings.DEFAULT_MAX_POOL_SIZE)
                .build();
        final AthenaConnectionSettings storedAthena = AthenaConnectionSettings.builder()
                .workGroup(AthenaConnectionSettings.DEFAULT_WORK_GROUP)
                .build();

        assertThat(DataConnectionSettingsPresenter.writeJdbc(view, null, storedJdbc)).isEqualTo(storedJdbc);
        assertThat(DataConnectionSettingsPresenter.writeAthena(view, null, storedAthena)).isEqualTo(storedAthena);
    }

    @Test
    void testStoredExcelSettingsAreKept() {
        Mockito.when(view.getSourceType()).thenReturn(DataConnectionType.EXCEL);
        Mockito.when(view.getExcelSheet()).thenReturn(" Teams ");
        Mockito.when(view.isExcelHeader()).thenReturn(false);
        final List<DataColumn> columns = List.of(new DataColumn("name", DataColumnType.TEXT));
        Mockito.when(view.getExcelColumns()).thenReturn(columns);
        final DataConnectionDoc doc = DataConnectionDoc.builder()
                .uuid("existing")
                .excel(ExcelSettings.builder()
                        .header(false)
                        .columns(columns)
                        .build())
                .build();

        final ExcelSettings excel = presenter.writeExcel(doc);

        assertThat(excel.getSheet()).isEqualTo("Teams");
        assertThat(excel.getHeader()).isFalse();
        assertThat(excel.getColumns()).isEqualTo(columns);
    }

    // What the editor shows for JDBC and Athena settings the document left unset.
    private void showJdbcAndAthenaDefaults() {
        Mockito.when(view.getConnectionProperties()).thenReturn("");
        Mockito.when(view.getMaxPoolSize()).thenReturn(JdbcConnectionSettings.DEFAULT_MAX_POOL_SIZE);
        Mockito.when(view.getFetchSize()).thenReturn(JdbcConnectionSettings.DEFAULT_FETCH_SIZE);
        Mockito.when(view.getWorkGroup()).thenReturn(AthenaConnectionSettings.DEFAULT_WORK_GROUP);
        Mockito.when(view.getCatalog()).thenReturn(AthenaConnectionSettings.DEFAULT_CATALOG);
        Mockito.when(view.getDatabase()).thenReturn(AthenaConnectionSettings.DEFAULT_DATABASE);
        Mockito.when(view.getAthenaMinRefreshMinutes())
                .thenReturn(AthenaConnectionSettings.DEFAULT_MIN_REFRESH_MINUTES);
    }
}
