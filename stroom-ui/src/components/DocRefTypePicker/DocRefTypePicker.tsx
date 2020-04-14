import * as React from "react";

import DocRefImage from "../DocRefImage";
import useDocRefTypes from "components/DocumentEditors/api/explorer/useDocRefTypes";
import Select, { components } from "react-select";
import { OptionProps, SingleValueProps } from "react-select";
import { ControlledInput } from "lib/useForm/types";
import useReactSelect from "lib/useReactSelect";
import { BasicOption } from "lib/useReactSelect/types";

interface Props extends ControlledInput<string> {
  invalidTypes?: string[];
}

const SingleValue: React.FunctionComponent<SingleValueProps<BasicOption>> = ({
  children,
  ...props
}) => {
  if (!!props.data) {
    return (
      <div className="DocRefTypePicker">
        <DocRefImage
          className="DocRefTypePicker__image"
          size="lg"
          docRefType={props.data.value}
        />
        <div className="DocRefTypePicker__text">{children}</div>
      </div>
    );
  } else {
    return (
      <components.SingleValue {...props}>{children}</components.SingleValue>
    );
  }
};

const Option: React.FunctionComponent<OptionProps<BasicOption>> = props => (
  <components.Option {...props}>
    <div className="DocRefTypePicker">
      <DocRefImage
        className="DocRefTypePicker__image"
        size="lg"
        docRefType={props.data.value}
      />
      <div className="DocRefTypePicker__text">{props.children}</div>
    </div>
  </components.Option>
);

let DocRefTypePicker = ({ value, onChange, invalidTypes = [] }: Props) => {
  const docRefTypes: string[] = useDocRefTypes();
  const options: string[] = React.useMemo(
    () => docRefTypes.filter(d => !invalidTypes.includes(d)),
    [docRefTypes, invalidTypes],
  );
  const { _onChange, _options, _value } = useReactSelect({
    options,
    onChange,
    value,
  });

  return (
    <Select
      value={_value}
      onChange={_onChange}
      options={_options}
      components={{ SingleValue, Option }}
    />
  );
};

export default DocRefTypePicker;
