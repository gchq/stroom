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

package stroom.upgrade;

import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestDDLPreprocessorUtil extends StroomUnitTest {
    @Test
    public void testSimple() {
        System.out.println(DDLPreprocessorUtil.processSQL("CREATE TABLE FOLDER (\n"
                + "'id' int NOT NULL auto_increment,\n" + "PRIMARY KEY (ID),\n" + "FK_FOLDER_ID int default NULL,\n"
                + "UNIQUE KEY FK_FOLDER_ID_NAME_IDX (FK_FOLDER_ID,NAME),\n" + "KEY FK_FOLDER_ID_IDX (FK_FOLDER_ID),\n"
                + "CONSTRAINT FOLDER_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET='latin1';"));

    }
}
