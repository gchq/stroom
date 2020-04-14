export interface Activity {
  id?: string;
  version?: number;
  createTimeMs?: number;
  createUser?: string;
  updateTimeMs?: number;
  updateUser?: string;
  userId?: string;
  details?: ActivityDetails;
}

export interface ActivityDetails {
  properties: Prop[];
}

export interface Prop {
  id: string;
  name: string;
  value: string;
  showInSelection: boolean;
  showInList: boolean;
}
