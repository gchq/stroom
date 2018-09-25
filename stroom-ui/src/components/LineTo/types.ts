export interface LineDefinition {
  lineId: string;
  lineType?: string;
  fromRect: DOMRect;
  toRect: DOMRect;
}

export type LineElementCreator = (ld: LineDefinition) => React.ReactNode;

export interface LineElementCreators {
  [lineType: string]: LineElementCreator;
}

export interface LineType {
  lineType?: string;
  fromId: string;
  toId: string;
}
