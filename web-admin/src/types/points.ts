export type TradingPointType =
    | 'COFFEE_SHOP'      // Кофейня
    | 'RESTAURANT'       // Ресторан
    | 'RETAIL'           // Магазин
    | 'SERVICE'          // Услуги
    | 'FLOWERS'          // Цветы
    | 'GIFTS'            // Подарки
    | 'CAKES'            // Торты
    | 'BARBERSHOP'       // Парикмахерская
    | 'CLOTHING'         // Одежда
    | 'TOYS'             // Игрушки
    | 'CAR_RENTAL'       // Аренда авто
    | 'SCOOTER_RENTAL'   // Аренда скутеров
    | 'AUTO_SERVICE'     // СТО
    | 'TIRE_SERVICE'     // Вулканизация
    | 'AUTO_PARTS'       // Автозапчасти
    | 'BANK'             // Банк
    | 'OTHER';           // Другое

export type WeekDayId =
    | 'MONDAY'
    | 'TUESDAY'
    | 'WEDNESDAY'
    | 'THURSDAY'
    | 'FRIDAY'
    | 'SATURDAY'
    | 'SUNDAY';

export interface WorkingIntervalDto {
    opensAt: string;
    closesAt: string;
}

export interface WorkingDayDto {
    day: WeekDayId;
    intervals: WorkingIntervalDto[];
}

export interface WeeklyScheduleDto {
    timezone?: string;
    days?: WorkingDayDto[];
}

export interface TradingPointDto {
    id: string;
    name: string;
    active: boolean;
    temporarilyPaused?: boolean;
    type: TradingPointType;
    address?: string;
    latitude?: number;
    longitude?: number;
    inviteCode?: string;
    currency: string;
    schedule?: WeeklyScheduleDto | null;
    rating?: number | null;
    reviewCount?: number;
    distanceMeters?: number | null;
    isOpenNow?: boolean | null;
    contactPhone?: string | null;
    contactLink?: string | null;
    additionalInfo?: string | null;
}

export interface TradingPointSearchResponse {
    points: TradingPointDto[];
    total: number;
    radiusMeters: number;
    limit: number;
    hasMore: boolean;
}

