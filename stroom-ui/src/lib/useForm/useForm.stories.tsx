import * as React from "react";
import { storiesOf } from "@storybook/react";
import useForm from ".";
import JsonDebug from "testing/JsonDebug";
import { PermissionInheritance } from "components/DocumentEditors/FolderExplorer/PermissionInheritancePicker/types";
import { ConditionType } from "components/ExpressionBuilder/types";
import PermissionInheritancePicker from "components/DocumentEditors/FolderExplorer/PermissionInheritancePicker";
import ConditionPicker from "components/ExpressionBuilder/ConditionPicker";

interface SimpleInputsFormValues {
  firstName: string;
  surname: string;
}

const SIMPLE_INITIAL_VALUES: SimpleInputsFormValues = {
  firstName: "John",
  surname: "Wick",
};

const SimpleInputsTestHarness: React.FunctionComponent = () => {
  const [validatedValue, onValidate] = React.useState<object>({});
  const { useTextInput, value } = useForm<SimpleInputsFormValues>({
    initialValues: SIMPLE_INITIAL_VALUES,
    onValidate,
  });
  const firstNameProps = useTextInput("firstName");
  const surnameProps = useTextInput("surname");

  return (
    <form>
      <label>First Name</label>
      <input {...firstNameProps} />
      <label>Surname</label>
      <input {...surnameProps} />
      <JsonDebug value={{ value, validatedValue }} />
    </form>
  );
};

interface ControlledInputsFormValues {
  permissionInheritance: PermissionInheritance;
  conditionType: ConditionType;
}

const ControlledInputTestHarness: React.FunctionComponent = () => {
  const { useControlledInputProps, value } = useForm<
    ControlledInputsFormValues
  >({});

  const permissionInheritanceProps = useControlledInputProps<
    PermissionInheritance
  >("permissionInheritance");
  const conditionTypeProps = useControlledInputProps<ConditionType>(
    "conditionType",
  );
  return (
    <form>
      <label>Permission Inheritance</label>
      <PermissionInheritancePicker {...permissionInheritanceProps} />
      <label>Condition type</label>
      <ConditionPicker {...conditionTypeProps} />
      <JsonDebug value={value} />
    </form>
  );
};

storiesOf("lib/useForm", module)
  .add("simpleInputs", () => <SimpleInputsTestHarness />)
  .add("controlledInputs", () => <ControlledInputTestHarness />);
