export interface InputProps {
  onChange: React.ChangeEventHandler<HTMLElement>;
  value: string;
}

export interface ControlledInput<T> {
  value: T;
  onChange: (v: T) => void;
}

export interface Form<T> {
  onUpdate: (updates: Partial<T>) => void;
  value: Partial<T>;
  useControlledInputProps: <FIELD_TYPE>(
    s: string,
  ) => ControlledInput<FIELD_TYPE>;
  useTextInput: (s: string) => InputProps;
  useCheckboxInput: (
    s: string,
  ) => {
    onChange: React.ChangeEventHandler<HTMLElement>;
    checked: any;
  };
}
