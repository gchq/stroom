import * as React from "react";
import { storiesOf } from "@storybook/react";
import JsonDebug from "testing/JsonDebug";
import CreateDocRefForm, { useThisForm } from "./CreateDocRefForm";

const stories = storiesOf("Document Editors/Folder/Create Doc Ref", module);

const TestHarness: React.FunctionComponent = () => {
  const { componentProps, value } = useThisForm();

  return (
    <div>
      <CreateDocRefForm {...componentProps} />
      <JsonDebug value={value} />
    </div>
  );
};

stories.add("Form", () => <TestHarness />);
