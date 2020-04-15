import InlineInput from "components/InlineInput/InlineInput";
import { ControlledInput } from "lib/useForm/types";
import * as React from "react";
import { useCallback } from "react";

interface Props extends ControlledInput<any> {
  valueType: string;
}

export const SingleValueWidget: React.FunctionComponent<Props> = ({
  value,
  onChange,
  valueType,
}) => {
  const handleChange = useCallback(
    value => {
      onChange(value);
    },
    [onChange],
  );
  return (
    <InlineInput type={valueType} value={value || ""} onChange={handleChange} />
  );
};

export default SingleValueWidget;
