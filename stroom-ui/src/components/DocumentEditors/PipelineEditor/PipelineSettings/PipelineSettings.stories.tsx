import * as React from "react";

import { storiesOf } from "@storybook/react";
import PipelineSettings, { useDialog } from "./PipelineSettings";
import { PipelineSettingsValues } from "../types";
import useUpdateableState from "lib/useUpdateableState";
import Button from "components/Button";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const { value, update } = useUpdateableState<PipelineSettingsValues>({
    description: "stuff",
  });

  const { componentProps, showDialog } = useDialog(update);

  const onClick = React.useCallback(() => showDialog(value), [
    value,
    showDialog,
  ]);

  return (
    <div>
      <Button onClick={onClick}>Show</Button>
      <JsonDebug value={value} />
      <PipelineSettings {...componentProps} />
    </div>
  );
};

storiesOf("Document Editors/Pipeline", module).add("Settings", () => (
  <TestHarness />
));
