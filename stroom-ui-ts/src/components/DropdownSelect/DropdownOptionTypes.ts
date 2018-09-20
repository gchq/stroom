export interface DropdownOptionType {
  text: string;
  value: string;
}

export interface DropdownOptionProps {
  option: DropdownOptionType;
  inFocus: boolean;
  onClick: () => void;
}
