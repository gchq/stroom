import * as React from "react";

import CreatableSelect from "react-select/creatable";

import { useIndexVolumeGroups } from "../indexVolumeGroupApi";
import { PickerProps, UsePickerProps, PickerBaseProps } from "./types";
import { useReactSelect } from "lib/useReactSelect";

const IndexVolumeGroupPicker: React.FunctionComponent<PickerProps> = ({
  value,
  onChange,
  valuesToFilterOut = [],
}) => {
  const { groups, createIndexVolumeGroup } = useIndexVolumeGroups();

  const options: string[] = React.useMemo(
    () =>
      groups.map((g) => g.name).filter((n) => !valuesToFilterOut.includes(n)),
    [groups, valuesToFilterOut],
  );
  const { _options, _onChange, _value } = useReactSelect({
    onChange,
    options,
    value,
  });

  const [isLoading, setIsLoading] = React.useState<boolean>(false);
  const onCreateOption = React.useCallback(
    (d: string) => {
      setIsLoading(true);
      createIndexVolumeGroup(d).then(() => {
        onChange(d);
        setIsLoading(false);
      });
    },
    [createIndexVolumeGroup, onChange, setIsLoading],
  );

  return (
    <CreatableSelect
      isLoading={isLoading}
      value={_value}
      onChange={_onChange}
      placeholder="Index Volume Group"
      onCreateOption={onCreateOption}
      options={_options}
    />
  );
};

export const usePicker = (baseProps?: PickerBaseProps): UsePickerProps => {
  const [value, onChange] = React.useState<string | undefined>(undefined);

  return {
    ...baseProps,
    value,
    onChange,
    reset: () => onChange(undefined),
  };
};

export default IndexVolumeGroupPicker;
