/*
 * Copyright 2018 Crown Copyright
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

import * as React from "react";
import { Tab, Tabs, TabList, TabPanel } from "react-tabs";

import { useMetaRow } from "components/MetaBrowser/api";
import Loader from "components/Loader";
import { MetaRow } from "../types";
import MetaDetails from "./MetaDetails";
import MetaAttributes from "./MetaAttributes";
import DataRetention from "./DataRetention";
import DataDisplay from "../DataDisplay";

interface Props {
  metaRow: MetaRow;
}

const MetaDetailTabs: React.FunctionComponent<Props> = ({ metaRow }) => {
  const dataRow = useMetaRow(metaRow.meta.id);

  if (!dataRow) {
    return <Loader message="Loading Data" />;
  }

  return (
    <Tabs>
      <TabList>
        <Tab>Data</Tab>
        <Tab>Details</Tab>
        <Tab>Attributes</Tab>
        <Tab>Retention</Tab>
      </TabList>

      <TabPanel>
        <DataDisplay metaRow={dataRow} />
      </TabPanel>
      <TabPanel>
        <MetaDetails dataRow={dataRow} />
      </TabPanel>
      <TabPanel>
        <MetaAttributes dataRow={dataRow} />
      </TabPanel>
      <TabPanel>
        <DataRetention dataRow={dataRow} />
      </TabPanel>
    </Tabs>
  );
};

export default MetaDetailTabs;
