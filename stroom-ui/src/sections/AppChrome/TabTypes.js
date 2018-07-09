// Enumerate the tab types that can be opened
const TabTypes = {
  DOC_REF: 0,
  EXPLORER_TREE: 1,
  TRACKER_DASHBOARD: 2,
  USER_ME: 3,
  AUTH_USERS: 4,
  AUTH_TOKENS: 5,
};

const pathPrefix = '/s';

const TabTypeDisplayInfo = {
  [TabTypes.DOC_REF]: {
    getTitle: tabData => tabData.name,
    path: `${pathPrefix}/docref/`,
    icon: 'file outline',
  },
  [TabTypes.EXPLORER_TREE]: {
    getTitle: () => 'Explorer',
    path: `${pathPrefix}/explorer`,
    icon: 'eye',
  },
  [TabTypes.TRACKER_DASHBOARD]: {
    getTitle: () => 'Trackers',
    path: `${pathPrefix}/trackers`,
    icon: 'tasks',
  },
  [TabTypes.USER_ME]: {
    getTitle: () => 'Me',
    path: `${pathPrefix}/me`,
    icon: 'user',
  },
  [TabTypes.AUTH_USERS]: {
    getTitle: () => 'Users',
    path: `${pathPrefix}/users`,
    icon: 'users',
  },
  [TabTypes.AUTH_TOKENS]: {
    getTitle: () => 'API Keys',
    path: `${pathPrefix}/apikeys`,
    icon: 'key',
  },
};

export { TabTypes, TabTypeDisplayInfo };

export default TabTypes;
