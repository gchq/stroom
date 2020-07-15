import * as React from "react";
import v4 from "uuid/v4";

import { storiesOf } from "@storybook/react";
import AddElementModal, { useDialog } from "./AddElementModal";
import Button from "components/Button";
import JsonDebug from "testing/JsonDebug";
import { NewElement } from "../types";
import { useElements } from "components/DocumentEditors/PipelineEditor/useElements";
import { ElementDefinition } from "components/DocumentEditors/PipelineEditor/useElements/types";
import Select from "react-select";

const PARENT_ID = v4();

const TestHarness: React.FunctionComponent = () => {
  const { elementDefinitions } = useElements();
  const existingNames = React.useMemo(
    () => elementDefinitions.map((e) => e.type),
    [elementDefinitions],
  );
  const [
    selectedElementDefinition,
    setSelectedElementDefinition,
  ] = React.useState<ElementDefinition>(elementDefinitions[0]);
  React.useEffect(() => {
    setSelectedElementDefinition(elementDefinitions[0]);
  }, [elementDefinitions]);
  const [newElement, setNewElement] = React.useState<NewElement | undefined>();
  const { componentProps, showDialog } = useDialog(setNewElement);

  const onClick = React.useCallback(() => {
    showDialog(PARENT_ID, selectedElementDefinition, existingNames);
  }, [selectedElementDefinition, existingNames, showDialog]);

  return (
    <div>
      <Select
        options={elementDefinitions}
        value={selectedElementDefinition}
        onChange={(x: ElementDefinition) => setSelectedElementDefinition(x)}
        getOptionLabel={(d) => d.type}
        getOptionValue={(d) => d.type}
      />
      <Button onClick={onClick} text="Show" />
      <JsonDebug
        value={{
          PARENT_ID,
          existingNames,
          selectedElementDefinition,
          newElement,
        }}
      />
      <AddElementModal {...componentProps} />
    </div>
  );
};

storiesOf("Document Editors/Pipeline/Add Element", module).add("Dialog", () => (
  <TestHarness />
));
