/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.importexport.server;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import stroom.node.server.GlobalPropertyService;
import stroom.node.server.MockStroomPropertyService;
import stroom.node.shared.GlobalProperty;
import stroom.util.config.StroomProperties;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomExpectedException;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomTestUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestContentPackImport {

    static Path CONTENT_PACK_DIR;

    static {
        String userHome = System.getProperty("user.home");
        CONTENT_PACK_DIR = Paths.get(userHome, StroomProperties.USER_CONF_DIR).resolve(ContentPackImport.CONTENT_PACK_IMPORT_DIR);
    }

    //This is needed as you can't have to RunWith annotations
    //so this is the same as @RunWith(MockitoJUnitRunner.class)
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    ImportExportService importExportService;
    MockStroomPropertyService stroomPropertyService = new MockStroomPropertyService();
    @Mock
    GlobalPropertyService globalPropertyService;
    @Captor
    ArgumentCaptor<GlobalProperty> globalPropArgCaptor;
    Path testPack1 = CONTENT_PACK_DIR.resolve("testPack1.zip");
    Path testPack2 = CONTENT_PACK_DIR.resolve("testPack2.zip");
    Path testPack3 = CONTENT_PACK_DIR.resolve("testPack3.badExtension");

    @Before
    public void setup() throws IOException {

        String userHome = System.getProperty("user.home");
        Path contentPackDir = Paths.get(userHome, StroomProperties.USER_CONF_DIR).resolve(ContentPackImport.CONTENT_PACK_IMPORT_DIR);
        Files.createDirectories(contentPackDir);
    }

    @After
    public void teardown() throws IOException {
        Files.deleteIfExists(testPack1);
        Files.deleteIfExists(testPack2);
        Files.deleteIfExists(testPack3);
    }


    @Test
    public void testStartup_disabled() throws IOException {
        ContentPackImport contentPackImport = new ContentPackImport(importExportService, stroomPropertyService);
        stroomPropertyService.setProperty(ContentPackImport.AUTO_IMPORT_ENABLED_PROP_KEY, "false");

        FileUtil.touch(testPack1);

        contentPackImport.startup();

        Mockito.verifyZeroInteractions(importExportService);
        Assert.assertTrue(Files.exists(testPack1));
    }

    @Test
    public void testStartup_enabledNoFiles() {
        ContentPackImport contentPackImport = new ContentPackImport(importExportService, stroomPropertyService);
        Mockito.when(globalPropertyService.loadByName(ContentPackImport.AUTO_IMPORT_ENABLED_PROP_KEY)).thenReturn(null);
        stroomPropertyService.setProperty(ContentPackImport.AUTO_IMPORT_ENABLED_PROP_KEY, "true");
        contentPackImport.startup();
        Mockito.verifyZeroInteractions(importExportService);
    }

    @Test
    public void testStartup_enabledThreeFiles() throws IOException {
        ContentPackImport contentPackImport = new ContentPackImport(importExportService, stroomPropertyService);
        Mockito.when(globalPropertyService.loadByName(ContentPackImport.AUTO_IMPORT_ENABLED_PROP_KEY))
                .thenReturn(null);
        stroomPropertyService.setProperty(ContentPackImport.AUTO_IMPORT_ENABLED_PROP_KEY, "true");

        FileUtil.touch(testPack1);
        FileUtil.touch(testPack2);
        FileUtil.touch(testPack3);

        contentPackImport.startup();
        Mockito.verify(importExportService, Mockito.times(1))
                .performImportWithoutConfirmation(testPack1);
        Mockito.verify(importExportService, Mockito.times(1))
                .performImportWithoutConfirmation(testPack2);
        //not a zip extension so should not be called
        Mockito.verify(importExportService, Mockito.times(0))
                .performImportWithoutConfirmation(testPack3);

        Assert.assertFalse(Files.exists(testPack1));

        //File should have moved into the imported dir
        Assert.assertFalse(Files.exists(testPack1));
        Assert.assertTrue(Files.exists(CONTENT_PACK_DIR.resolve(ContentPackImport.IMPORTED_DIR).resolve(testPack1.getFileName())));
    }

    @Test
    @StroomExpectedException(exception = RuntimeException.class)
    public void testStartup_failedImport() throws IOException {
        ContentPackImport contentPackImport = new ContentPackImport(importExportService, stroomPropertyService);
        Mockito.when(globalPropertyService.loadByName(ContentPackImport.AUTO_IMPORT_ENABLED_PROP_KEY))
                .thenReturn(null);

        Mockito.doThrow(new RuntimeException("Error thrown by mock import service for test"))
                .when(importExportService)
                .performImportWithoutConfirmation(Matchers.any());
        stroomPropertyService.setProperty(ContentPackImport.AUTO_IMPORT_ENABLED_PROP_KEY, "true");

        FileUtil.touch(testPack1);

        contentPackImport.startup();

        //File should have moved into the failed dir
        Assert.assertFalse(Files.exists(testPack1));
        Assert.assertTrue(Files.exists(CONTENT_PACK_DIR.resolve(ContentPackImport.FAILED_DIR).resolve(testPack1.getFileName())));
    }
}
