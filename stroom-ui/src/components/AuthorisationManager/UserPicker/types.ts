export interface BaseProps {
  isGroup?: boolean;
  // If we are picking a group to add to an existing list...we should
  // not present those existing items as options
  valuesToFilterOut?: string[];
}

export interface Props extends BaseProps {
  value?: string;
  onChange: (v: string) => any;
}

export interface UseProps {
  pickerProps: Props;
  reset: () => void;
}
