import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import CopyMoveDocRefForm, { useThisForm } from "./CopyMoveDocRefForm";
import JsonDebug from "testing/JsonDebug";
import fullTestData from "testing/data";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";

const testFolder2 = fullTestData.documentTree.children![1];

interface Props {
  testDestination?: DocRefType;
}

const TestHarness: React.FunctionComponent<Props> = ({ testDestination }) => {
  const { componentProps, value } = useThisForm({
    initialDestination: testDestination,
  });

  return (
    <div>
      <CopyMoveDocRefForm {...componentProps} />
      <JsonDebug value={value} />
    </div>
  );
};

const storiesPreFilled = storiesOf(
  "Document Editors/Folder/Copy Doc Ref/Form",
  module,
);
addThemedStories(storiesPreFilled, "preFilled", () => (
  <TestHarness testDestination={testFolder2} />
));

const storiesBlankSlate = storiesOf(
  "Document Editors/Folder/Copy Doc Ref/Form",
  module,
);
addThemedStories(storiesBlankSlate, "blankSlate", () => <TestHarness />);
