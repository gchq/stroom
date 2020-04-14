import * as React from "react";

import { Tab, Tabs, TabList, TabPanel } from "react-tabs";
import DocRefEditor, { useDocRefEditor } from "../DocRefEditor";
import { SwitchedDocRefEditorProps } from "../DocRefEditor/types";
import MetaBrowser from "components/MetaBrowser";
import ActiveTasks from "./ActiveTasks";
import FeedSettings from "./FeedSettings";
import useDocument from "../api/explorer/useDocument";

const FeedEditor: React.FunctionComponent<SwitchedDocRefEditorProps> = ({
  docRefUuid,
}) => {
  const { editorProps } = useDocRefEditor({ docRefUuid });
  const {
    node: { name },
  } = useDocument(docRefUuid);

  return (
    <DocRefEditor {...editorProps}>
      <Tabs>
        <TabList>
          <Tab>Data</Tab>
          <Tab>Active Tasks</Tab>
          <Tab>Settings</Tab>
        </TabList>
        <TabPanel>
          <MetaBrowser feedName={name} />
        </TabPanel>
        <TabPanel>
          <ActiveTasks feedUuid={docRefUuid} />
        </TabPanel>
        <TabPanel>
          <FeedSettings feedUuid={docRefUuid} />
        </TabPanel>
      </Tabs>
    </DocRefEditor>
  );
};

export default FeedEditor;
