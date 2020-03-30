import * as React from "react";
import { useState } from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";

import DeletePipelineElement, { useDialog } from ".";
import Button from "components/Button";
import JsonDebug from "testing/JsonDebug";

const stories = storiesOf("Document Editors/Pipeline/Delete Element", module);

let nextElementId = 12;

const TestHarness: React.FunctionComponent = () => {
  const [elementDeleted, setElementDeleted] = useState<string | undefined>(
    undefined,
  );

  const { componentProps, showDialog } = useDialog(setElementDeleted);

  return (
    <div>
      <Button text="Show" onClick={() => showDialog(`${nextElementId++}`)} />
      <DeletePipelineElement {...componentProps} />
      <JsonDebug value={{ elementDeleted }} />
    </div>
  );
};

addThemedStories(stories, () => <TestHarness />);
