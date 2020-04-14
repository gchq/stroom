import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import JsonDebug from "testing/JsonDebug";
import RenameDocRefForm, { useThisForm } from "./RenameDocRefForm";
import fullTestData from "testing/data";

const testDocRef = fullTestData.documentTree.children![0].children![0]
  .children![0];

const stories = storiesOf(
  "Document Editors/Folder/Rename Doc Ref/Form",
  module,
);

const TestHarness: React.FunctionComponent = () => {
  const { value, componentProps } = useThisForm(testDocRef);

  return (
    <div>
      <RenameDocRefForm {...componentProps} />
      <JsonDebug value={value} />
    </div>
  );
};

addThemedStories(stories, () => <TestHarness />);
