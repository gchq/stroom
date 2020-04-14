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
    s: keyof T,
  ) => ControlledInput<FIELD_TYPE>;
  useTextInput: (s: keyof T) => InputProps;
  useCheckboxInput: (
    s: keyof T,
  ) => {
    onChange: React.ChangeEventHandler<HTMLElement>;
    checked: any;
  };
}
