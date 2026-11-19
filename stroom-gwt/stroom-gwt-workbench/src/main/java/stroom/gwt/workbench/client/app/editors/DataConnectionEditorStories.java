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

package stroom.gwt.workbench.client.app.editors;

import stroom.dataconnection.client.component.DataConnectionEditor;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionResource;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.UploadReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Editors/DataConnectionEditor` stories, showing Stroom's real [DataConnectionEditor] (a
/// Data Connection's tab: Settings, Documentation and Permissions) with fake REST replies.
///
/// As `DataConnectionPlugin` does, the story fetches the document (`GET /dataConnection/v1/{uuid}`)
/// and reads it into the editor ([DocEditors#open]). The other routes:
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /dataConnection/v1/testConnection` | Test Connection (Check CSV, Check Workbook) |
/// | `POST /dataConnection/v1/detectColumns` | Detect Columns and Detect Attributes |
/// | `POST /credentials/findCredentials` (and `getByName` for the saved one) | the credentials |
/// | the workbook's upload (`importfile.rpc`) | an Excel workbook chosen |
/// | the Permissions tab's routes | the Permissions tab |
public final class DataConnectionEditorStories {

    private static final DocRef DOC_REF = new DocRef(DataConnectionDoc.TYPE, "dc-1", "My Connection");

    // DataConnectionResource.fetch(): a JDBC connection, with settings of the other types kept
    private static final String JDBC_DOC = """
            {"type": "DataConnection", "uuid": "dc-1", "name": "My Connection",
              "description": "# Connection docs", "sourceType": "JDBC",
              "jdbc": {"url": "jdbc:mysql://reporting-db:3306/reports", "credentialName": "db-reader"}}""";

    // An Amazon Athena connection, with a data scan limit
    private static final String ATHENA_DOC = """
            {"type": "DataConnection", "uuid": "dc-1", "name": "My Connection", "sourceType": "ATHENA",
              "athena": {"region": "eu-west-2", "workGroup": "stroom", "database": "logs",
                "outputLocation": "s3://my-bucket/athena/", "maxDataScannedMb": 500}}""";

    // A CSV connection with data and one column
    private static final String CSV_DOC = """
            {"type": "DataConnection", "uuid": "dc-1", "name": "My Connection", "sourceType": "CSV",
              "csv": {"data": "team,size\\nRed,5\\nBlue,7\\n", "columns": [{"name": "team", "type": "TEXT"}]}}""";

    // An LDAP connection reading three attributes
    private static final String LDAP_DOC = """
            {"type": "DataConnection", "uuid": "dc-1", "name": "My Connection", "sourceType": "LDAP",
              "ldap": {"url": "ldaps://directory.example.org:636", "baseDn": "ou=People,dc=example,dc=org",
                "attributes": [{"name": "cn", "type": "TEXT"}, {"name": "mail", "type": "TEXT"},
                  {"name": "uid", "type": "TEXT"}]}}""";

    // A document as first made: no type and no settings, so every value shown is a default
    private static final String NEW_DOC = """
            {"type": "DataConnection", "uuid": "dc-1", "name": "My Connection"}""";

    // An Excel connection with no workbook yet
    private static final String EXCEL_DOC = """
            {"type": "DataConnection", "uuid": "dc-1", "name": "My Connection", "sourceType": "EXCEL",
              "excel": {}}""";

    // CredentialsResource.findCredentials(): the credentials the pickers list
    private static final String CREDENTIALS = """
            {"values": [{"uuid": "c1", "name": "db-reader", "credentialType": "USERNAME_PASSWORD"},
                {"uuid": "c2", "name": "db-reader-2", "credentialType": "USERNAME_PASSWORD"}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    // DataConnectionResource.detectColumns(): the columns found
    private static final String DETECTED_COLUMNS = """
            [{"name": "team", "type": "TEXT"}, {"name": "size", "type": "LONG"}]""";

    private static final String TEST_CONNECTION = "/dataConnection/v1/testConnection";
    private static final String DETECT_COLUMNS = "/dataConnection/v1/detectColumns";

    // A FormGroup gives its control the group's identity as its id
    private static final String TYPE = "#dataConnectionType";
    private static final String JDBC_URL = "#dataConnectionJdbcUrl";
    private static final String JDBC_CREDENTIAL = "#dataConnectionJdbcCredential";
    private static final String CSV_FILE = "#dataConnectionCsvFile";
    private static final String EXCEL_WORKBOOK = "#dataConnectionExcelWorkbook";

    private DataConnectionEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/DataConnectionEditor", DataConnectionEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, JDBC_DOC, false))
                .withPlay(play -> {
                    for (final String label : new String[]{"Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.waitFor(() -> play.expect(play.querySelector(JDBC_URL))
                            .toHaveValue("jdbc:mysql://reporting-db:3306/reports"));
                    play.expect(selectionBox(play, TYPE)).toHaveValue("JDBC");
                    // The credential picker shows the saved credential (fetched by name)
                    play.waitFor(() -> play.expect(selectionBox(play, JDBC_CREDENTIAL)).toHaveValue("db-reader"));
                    // Only the JDBC section is shown
                    play.expect(play.getByText("JDBC URL", "label")).toBeVisible();
                    play.expect(play.getByText("AWS Region", "label")).not().toBeVisible();
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // An Athena connection: its section only, with its values, and the defaults of those
                // it leaves unset
                .story("Athena", context -> render(context, ATHENA_DOC, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector("#dataConnectionRegion"))
                            .toHaveValue("eu-west-2"));
                    play.expect(selectionBox(play, TYPE)).toHaveValue("Amazon Athena");
                    play.expect(play.querySelector("#dataConnectionWorkGroup")).toHaveValue("stroom");
                    play.expect(play.querySelector("#dataConnectionCatalog")).toHaveValue("AwsDataCatalog");
                    play.expect(play.getByText("JDBC URL", "label")).not().toBeVisible();
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                .story("EditEnablesSave", context -> render(context, JDBC_DOC, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(JDBC_URL))
                            .toHaveValue("jdbc:mysql://reporting-db:3306/reports"));
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.type(play.querySelector(JDBC_URL), "?ssl=true");
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // Undoing an edit makes the document clean again: the defaults shown for what the
                // document leaves unset (here everything) aren't written into it
                .story("UndoIsClean", context -> render(context, NEW_DOC, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(selectionBox(play, TYPE)).toHaveValue("JDBC"));
                    play.type(play.querySelector(JDBC_URL), "jdbc:mysql://db:3306/r");
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.clear(play.querySelector(JDBC_URL));
                    play.tab();
                    play.waitFor(() -> play.expect(save).toHaveAttribute("aria-disabled", "true"));
                    DocEditors.expectNoProblems(play);
                })
                // As UndoIsClean, for a JDBC document with no settings of the other types
                .story("UndoIsCleanJdbc", context -> render(context, JDBC_DOC, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(JDBC_URL))
                            .toHaveValue("jdbc:mysql://reporting-db:3306/reports"));
                    play.type(play.querySelector(JDBC_URL), "?ssl=true");
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.clear(play.querySelector(JDBC_URL));
                    play.type(play.querySelector(JDBC_URL), "jdbc:mysql://reporting-db:3306/reports");
                    play.tab();
                    play.waitFor(() -> play.expect(save).toHaveAttribute("aria-disabled", "true"));
                    DocEditors.expectNoProblems(play);
                })
                // Choosing a type shows its section only, and names the test for it
                .story("TypeSections", context -> render(context, JDBC_DOC, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(selectionBox(play, TYPE)).toHaveValue("JDBC"));
                    chooseType(play, "Amazon Athena");
                    play.waitFor(() -> play.expect(play.getByText("AWS Region", "label")).toBeVisible());
                    play.expect(play.getByText("JDBC URL", "label")).not().toBeVisible();
                    // Athena takes the time range in its SQL, so has no time column
                    play.expect(play.getByText("Time Column (optional)", "label")).not().toBeVisible();
                    chooseType(play, "CSV");
                    play.waitFor(() -> play.expect(play.getByRole("button", StroomDom.button("Check CSV")))
                            .toBeVisible());
                    // CSV is read in Stroom, so has no query timeout
                    play.expect(play.getByText("Query Timeout (seconds)", "label")).not().toBeVisible();
                    chooseType(play, "Excel");
                    play.waitFor(() -> play.expect(play.getByRole("button", StroomDom.button("Check Workbook")))
                            .toBeVisible());
                    chooseType(play, "LDAP");
                    play.waitFor(() -> play.expect(play.getByRole("button", StroomDom.button("Detect Attributes")))
                            .toBeVisible());
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // A saved credential the user can't see: a note says so, and the document keeps it
                .story("CredentialNotFound", context -> render(context, JDBC_DOC, false, "null"))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText(
                            "Credential 'db-reader' was not found, or you do not have Use permission on it. "
                            + "It is kept unless you choose another.")).toBeVisible());
                    play.expect(selectionBox(play, JDBC_CREDENTIAL)).toHaveValue("");
                    DocEditors.expectNoProblems(play);
                })
                .story("TestConnectionSuccess", context -> render(context, JDBC_DOC, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(selectionBox(play, JDBC_CREDENTIAL)).toHaveValue("db-reader"));
                    play.click(play.findByRole("button", StroomDom.button("Test Connection")));
                    play.waitFor(() -> play.expect(screen.getByText("Connection Success")).toBeInTheDocument());
                    play.expect(screen.getByText("Connected to MySQL 8.4.")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(TEST_CONNECTION)
                                    .withJsonBodyContaining("{\"jdbc\": {\"credentialName\": \"db-reader\"}}")
                                    .toSpyMatcher());
                    DocEditors.expectNoUnhandledRequests(play);
                })
                // Detect Columns fills the grid from the CSV as edited
                .story("CsvDetectColumns", context -> render(context, CSV_DOC, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.getByRole("grid", "Columns")).toBeVisible());
                    play.expect(play.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(1);
                    play.click(play.getByRole("button", StroomDom.button("Detect Columns")));
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(2));
                    play.expect(play.getByRole("combobox", "Type of size")).toHaveValue("Long");
                    play.expect(save).not().toHaveAttribute("aria-disabled");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(DETECT_COLUMNS)
                                    .withJsonBodyContaining("{\"sourceType\": \"CSV\"}")
                                    .toSpyMatcher());
                    DocEditors.expectNoProblems(play);
                })
                // Load From File reads the file in the browser into the editor, then detects its columns
                .story("CsvLoadFromFile", context -> render(context, CSV_DOC, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByRole("grid", "Columns")).toBeVisible());
                    play.upload(play.querySelector(CSV_FILE + " " + StroomDom.FILE_INPUT),
                            "teams.csv", "team,size\nGreen,3\n", "text/csv");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(DETECT_COLUMNS)
                                    .withJsonBodyContaining("{\"csv\": {\"data\": \"team,size\\nGreen,3\\n\"}}")
                                    .toSpyMatcher()));
                    // Read, not uploaded
                    play.expect(play.spy(ScreenHarness.UPLOAD_SPY)).not().toHaveBeenCalled();
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(2));
                    DocEditors.expectNoProblems(play);
                })
                // Add Attribute asks for a name, refusing one already listed (whatever its case)
                .story("LdapAddAttribute", context -> render(context, LDAP_DOC, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByRole("grid", "Attributes")).toBeVisible());
                    play.expect(play.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(3);
                    play.click(play.getByRole("button", "Add Attribute"));
                    final Query name = screen.findByLabelText("Name");
                    play.type(name, "MAIL");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText("'mail' is already listed")).toBeVisible());
                    play.clear(name);
                    play.type(name, "telephoneNumber");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(4));
                    play.expect(play.getByRole("combobox", "Type of telephoneNumber")).toHaveValue("Text");
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Add Attribute left open (the screenshot and the dialog measurement show it)
                .story("AddAttributeOpen", context -> render(context, LDAP_DOC, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByRole("grid", "Attributes")).toBeVisible());
                    play.click(play.getByRole("button", "Add Attribute"));
                    final Play dialog = screen.within(screen.findByText("Add Attribute", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    dialog.findByLabelText("Name");
                })
                // Remove selects the attribute now in the removed one's place
                .story("LdapRemoveAttribute", context -> render(context, LDAP_DOC, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByRole("grid", "Attributes")).toBeVisible());
                    play.click(play.getByText("mail"));
                    play.click(play.getByRole("button", "Remove Attribute"));
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(2));
                    play.expect(play.getByText("uid").closest(StroomDom.GRID_ROW)).toHaveClass(StroomDom.SELECTED_ROW);
                    DocEditors.expectNoProblems(play);
                })
                // A workbook is uploaded as soon as it is chosen, then its columns are detected
                .story("ExcelUpload", context -> render(context, EXCEL_DOC, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("No workbook has been uploaded.")).toBeVisible());
                    play.upload(play.querySelector(EXCEL_WORKBOOK + " " + StroomDom.FILE_INPUT),
                            "teams.xlsx", "x", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    play.waitFor(() -> play.expect(play.getByText("teams.xlsx (uploaded; stored when you save)"))
                            .toBeVisible());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(DETECT_COLUMNS)
                                    .withJsonBodyContaining("{\"excel\": {\"fileName\": \"teams.xlsx\"}}")
                                    .toSpyMatcher()));
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // A file dialog cancelled clears the input, a change with no file: nothing is read
                // or uploaded, and nothing is said
                .story("CancelledFileChoice", context -> render(context, EXCEL_DOC, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("No workbook has been uploaded.")).toBeVisible());
                    play.fireEvent().change(play.querySelector(EXCEL_WORKBOOK + " " + StroomDom.FILE_INPUT), "");
                    play.fireEvent().change(play.querySelector(CSV_FILE + " " + StroomDom.FILE_INPUT), "");
                    play.expect(play.getByText("No workbook has been uploaded.")).toBeVisible();
                    play.expect(play.spy(ScreenHarness.UPLOAD_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, JDBC_DOC, true))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save (read only)");
                    // Read only, not disabled: every field can be read and copied, none changed
                    play.waitFor(() -> play.expect(play.querySelector(JDBC_URL)).toHaveAttribute("readonly"));
                    play.type(play.querySelector(JDBC_URL), "x");
                    play.expect(play.querySelector(JDBC_URL)).toHaveValue("jdbc:mysql://reporting-db:3306/reports");
                    play.expect(play.querySelector(TYPE)).toHaveClass("selection-box--read-only");
                    // Testing needs permission to edit the document
                    play.expect(play.getByRole("button", StroomDom.button("Test Connection"))).toBeDisabled();
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // The LDAP settings, read only: no attribute can be added or removed
                .story("LdapReadOnly", context -> render(context, LDAP_DOC, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByRole("grid", "Attributes")).toBeVisible());
                    // A toolbar's buttons say they are disabled, and stay in its keyboard order
                    play.expect(play.getByRole("button", "Add Attribute")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", "Remove Attribute"))
                            .toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", StroomDom.button("Detect Attributes"))).toBeDisabled();
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                });
    }

    // A SelectionBox's text box, which shows the chosen value
    private static Query selectionBox(final Play play, final String id) {
        return play.within(play.querySelector(id)).querySelector(StroomDom.SELECTION_BOX);
    }

    private static void chooseType(final Play play, final String type) {
        play.click(selectionBox(play, TYPE));
        play.click(play.screen().findByRole("option", type));
    }

    private static Widget render(final StoryContext context, final String docJson, final boolean readOnly) {
        return render(context, docJson, readOnly,
                "{\"uuid\": \"c1\", \"name\": \"db-reader\", \"credentialType\": \"USERNAME_PASSWORD\"}");
    }

    private static Widget render(final StoryContext context,
                                 final String docJson,
                                 final boolean readOnly,
                                 final String savedCredential) {
        final RestFixtures fixtures = DocEditors.permissionRoutes(RestFixtures.builder())
                .get("/dataConnection/v1/dc-1", RestReply.json(docJson))
                .post("/credentials/findCredentials", RestReply.json(CREDENTIALS))
                .post("/credentials/getByName", RestReply.json(savedCredential))
                .post(TEST_CONNECTION, RestReply.json("{\"ok\": true, \"message\": \"Connected to MySQL 8.4.\"}"))
                .post(DETECT_COLUMNS, RestReply.json(DETECTED_COLUMNS))
                .upload(UploadReply.success("res-1", "teams.xlsx"))
                .build();
        final DataConnectionResource resource = GWT.create(DataConnectionResource.class);
        return DocEditors.render(context, fixtures, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getDataConnectionEditor(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
