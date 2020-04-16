export interface LineDefinition {
  lineId: string;
  fromRect: DOMRect;
  toRect: DOMRect;
}

export type LineElementCreator = React.FunctionComponent<LineDefinition>;

export interface LineType {
  lineId: string;
  fromId: string;
  toId: string;
}

export interface LineContextApi {
  lineContextId: string;
  rawLines: LineType[];
  getEndpointId: (identity: string) => string;
  createLine: (line: LineType) => void;
  destroyLine: (lineId: string) => void;
}
