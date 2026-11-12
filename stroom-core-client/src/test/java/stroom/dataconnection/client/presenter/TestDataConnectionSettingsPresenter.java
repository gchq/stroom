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
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.ExcelSettings;

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
}
