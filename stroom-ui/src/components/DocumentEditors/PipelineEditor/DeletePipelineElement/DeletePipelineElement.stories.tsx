import * as React from "react";
import { useState } from "react";

import { storiesOf } from "@storybook/react";

import DeletePipelineElement, { useDialog } from ".";
import Button from "components/Button";
import JsonDebug from "testing/JsonDebug";

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

storiesOf("Document Editors/Pipeline", module).add("Delete Element", () => (
  <TestHarness />
));
