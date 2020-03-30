import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import JsonDebug from "testing/JsonDebug";
import CreateDocRefForm, { useThisForm } from "./CreateDocRefForm";

const stories = storiesOf(
  "Document Editors/Folder/Create Doc Ref/Form",
  module,
);

const TestHarness: React.FunctionComponent = () => {
  const { componentProps, value } = useThisForm();

  return (
    <div>
      <CreateDocRefForm {...componentProps} />
      <JsonDebug value={value} />
    </div>
  );
};

addThemedStories(stories, () => <TestHarness />);
