import * as React from "react";
import Select from "react-select";
import CreatableSelect from "react-select/creatable";
import {
  StroomUser,
  useManageUsers,
} from "components/AuthorisationManager/api/userGroups";
import { BaseProps, Props, UseProps } from "./types";

const DEFAULT_USER_UUIDS_TO_FILTER_OUT: string[] = [];

interface UserOption {
  value: string;
  label: string;
}

const UserPicker: React.FunctionComponent<Props> = ({
  value,
  onChange,
  isGroup,
  valuesToFilterOut = DEFAULT_USER_UUIDS_TO_FILTER_OUT,
}) => {
  const { findUsers, users, createUser } = useManageUsers();
  React.useEffect(() => {
    findUsers(undefined, isGroup, undefined);
  }, [isGroup, findUsers]);

  const [isLoading, setIsLoading] = React.useState<boolean>(false);
  const onCreateOption = React.useCallback(
    (d) => {
      setIsLoading(true);
      createUser(d, isGroup !== undefined ? isGroup : false).then(
        (newUser: StroomUser) => {
          onChange(newUser.uuid);
          setIsLoading(false);
        },
      );
    },
    [isGroup, setIsLoading, onChange, createUser],
  );

  const options: UserOption[] = React.useMemo(
    () =>
      users
        .filter((user) => !valuesToFilterOut.includes(user.uuid))
        .map((g) => ({
          value: g.uuid,
          label: g.name,
        })),
    [users, valuesToFilterOut],
  );
  return isGroup ? (
    <CreatableSelect
      isLoading={isLoading}
      value={options.find((o) => o.value === value)}
      onChange={(o: UserOption) => onChange(o.value)}
      placeholder="Index Volume Group"
      onCreateOption={onCreateOption}
      options={options}
    />
  ) : (
    <Select
      value={options.find((o) => o.value === value)}
      onChange={(o: UserOption) => onChange(o.value)}
      placeholder="User"
      options={options}
    />
  );
};

export const usePicker = ({
  isGroup,
  valuesToFilterOut,
}: BaseProps): UseProps => {
  const [value, onChange] = React.useState<string | undefined>(undefined);

  return {
    pickerProps: { value, onChange, isGroup, valuesToFilterOut },
    reset: () => onChange(undefined),
  };
};

export default UserPicker;
