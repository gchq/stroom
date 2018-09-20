export interface DocRefType {
  uuid: string;
  type: string;
  name?: string;
}

export type DocRefConsumer = (d: DocRefType) => void;

export interface SelectOptionType {
  text: string;
  value: string;
}
