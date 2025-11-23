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

export enum AnalyticsPeriod {
    WEEK = 'WEEK',
    MONTH = 'MONTH',
    SIX_MONTHS = 'SIX_MONTHS',
    YEAR = 'YEAR'
}

