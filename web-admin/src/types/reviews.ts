export type ReviewType = 'CLIENT_TO_SERVICE' | 'CASHIER_TO_CLIENT';

export interface ReviewDto {
    id: string;
    type: ReviewType;
    rating: number;
    tags: string[];
    comment?: string;
    createdAt: number;
    pointName: string;
    authorName: string;
    authorPhone?: string;
    targetName?: string; // For CASHIER_TO_CLIENT
    targetPhone?: string;
}

export interface TagStatDto {
    tag: string;
    count: number;
}

export interface HeatmapPointDto {
    pointId: string;
    pointName: string;
    tagStats: TagStatDto[];
}

export interface AnalyticsDataDto {
    nps: number;
    averageRating: number;
    totalReviews: number;
    heatmap: HeatmapPointDto[];
    series?: NpsSeriesPointDto[];
}

export interface NpsSeriesPointDto {
    date: number;
    nps: number;
    reviews: number;
    averageRating: number;
}

