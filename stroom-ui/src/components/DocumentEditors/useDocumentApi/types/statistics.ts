import { DocumentBase } from "./base";

export type StatisticType = "Count" | "Value";
export type StatisticRollupType = "None" | "All" | "Custom";
export interface StatisticField {
  fieldName: string;
}
export interface CustomRollupMask {
  rolledUpTagPositions: number[];
}
export interface StatisticsDataSourceData {
  statisticFields: StatisticField[];
  customRollUpMasks: CustomRollupMask[];
  fieldPositionMap: {
    [fieldName: string]: number;
  };
}
export interface StatisticStoreDoc extends DocumentBase<"StatisticStore"> {
  description?: string;
  statisticType?: StatisticType;
  rollUpType?: StatisticRollupType;
  precision?: number;
  enabled?: boolean;
  config?: StatisticsDataSourceData;
}
export interface StroomStatsStoreEntityData {
  statisticFields: StatisticField[];
  customRollUpMasks: CustomRollupMask[];
  fieldPositionMap: {
    [fieldName: string]: number;
  };
}
export interface StroomStatsStoreDoc extends DocumentBase<"StroomStatsStore"> {
  description?: string;
  statisticType?: StatisticType;
  rollUpType?: StatisticRollupType;
  precision?: number;
  enabled?: boolean;
  config?: StroomStatsStoreEntityData;
}
