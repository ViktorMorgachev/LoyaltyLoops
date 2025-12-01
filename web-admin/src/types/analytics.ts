export interface RevenueChartPoint {
    date: string;
    revenue: number;
    transactionsCount: number;
}

export interface AnalyticsResponse {
    totalRevenue: number;
    totalTransactions: number;
    averageCheck: number;
    chartData: RevenueChartPoint[];
}

export const AnalyticsPeriod = {
    WEEK: 'WEEK',
    MONTH: 'MONTH',
    SIX_MONTHS: 'SIX_MONTHS',
    YEAR: 'YEAR',
} as const;

export type AnalyticsPeriod = (typeof AnalyticsPeriod)[keyof typeof AnalyticsPeriod];

