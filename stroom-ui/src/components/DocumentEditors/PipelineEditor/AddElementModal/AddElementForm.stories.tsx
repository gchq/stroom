import * as React from "react";
import { storiesOf } from "@storybook/react";

import AddElementForm, { useThisForm } from "./AddElementForm";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import useElements from "../useElements";
import JsonDebug from "testing/JsonDebug";

const existingNames: string[] = ["Tom", "Dick", "Harry"];

const TestHarness: React.FunctionComponent = () => {
  const { elementDefinitions } = useElements();
  const { value, componentProps } = useThisForm({
    existingNames,
    elementDefinition: elementDefinitions[0],
  });

  return (
    <div>
      <AddElementForm {...componentProps} />
      <JsonDebug value={{ value, existingNames }} />
    </div>
  );
};

const stories = storiesOf("Document Editors/Pipeline/Add Element", module);

addThemedStories(stories, "Form", () => <TestHarness />);
