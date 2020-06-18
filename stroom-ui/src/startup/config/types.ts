export interface ThemeConfig {
  backgroundAttachment?: string;
  backgroundColor?: string;
  backgroundImage?: string;
  backgroundPosition?: string;
  backgroundRepeat?: string;
  backgroundOpacity?: string;
  tubeVisible?: string;
  tubeOpacity?: string;
  labelColours?: string;
}

export interface UiPreferences {
  dateFormat?: string;
}

export interface UiConfig {
  theme: ThemeConfig;
  uiPreferences: UiPreferences;
}
