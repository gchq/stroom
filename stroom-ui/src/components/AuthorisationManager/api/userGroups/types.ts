export interface StroomUser {
  uuid: string;
  name: string;
  group: boolean;
}

export interface StoreState {
  allUsers: StroomUser[];
  usersInGroup: {
    [s: string]: StroomUser[];
  };
  groupsForUser: {
    [s: string]: StroomUser[];
  };
}
