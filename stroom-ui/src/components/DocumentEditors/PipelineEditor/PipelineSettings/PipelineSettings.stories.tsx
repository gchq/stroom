import * as React from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import PipelineSettings, { useDialog } from "./PipelineSettings";
import { PipelineSettingsValues } from "../types";
import useUpdateableState from "lib/useUpdateableState";
import Button from "components/Button";
import JsonDebug from "testing/JsonDebug";

const stories = storiesOf("Document Editors/Pipeline", module);

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
      <Button onClick={onClick} text="Show" />
      <JsonDebug value={value} />
      <PipelineSettings {...componentProps} />
    </div>
  );
};

addThemedStories(stories, "Settings", () => <TestHarness />);
