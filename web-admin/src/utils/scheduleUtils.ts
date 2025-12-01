import type { WeekDayId, WeeklyScheduleDto, WorkingIntervalDto } from '../types/points';

export const WEEK_DAY_ORDER: { id: WeekDayId; shortKey: string; titleKey: string }[] = [
    { id: 'MONDAY', shortKey: 'point_details.schedule_days_short.mon', titleKey: 'point_details.schedule_days.mon' },
    { id: 'TUESDAY', shortKey: 'point_details.schedule_days_short.tue', titleKey: 'point_details.schedule_days.tue' },
    { id: 'WEDNESDAY', shortKey: 'point_details.schedule_days_short.wed', titleKey: 'point_details.schedule_days.wed' },
    { id: 'THURSDAY', shortKey: 'point_details.schedule_days_short.thu', titleKey: 'point_details.schedule_days.thu' },
    { id: 'FRIDAY', shortKey: 'point_details.schedule_days_short.fri', titleKey: 'point_details.schedule_days.fri' },
    { id: 'SATURDAY', shortKey: 'point_details.schedule_days_short.sat', titleKey: 'point_details.schedule_days.sat' },
    { id: 'SUNDAY', shortKey: 'point_details.schedule_days_short.sun', titleKey: 'point_details.schedule_days.sun' },
];

export interface ScheduleDisplayRow {
    dayId: WeekDayId;
    intervals: WorkingIntervalDto[];
    isDayOff: boolean;
}

export const normalizeSchedule = (schedule?: WeeklyScheduleDto | null): ScheduleDisplayRow[] => {
    if (!schedule?.days?.length) {
        return WEEK_DAY_ORDER.map(({ id }) => ({
            dayId: id,
            intervals: [],
            isDayOff: true,
        }));
    }
    const map = new Map<WeekDayId, WorkingIntervalDto[]>();
    schedule.days.forEach((day) => {
        map.set(day.day, day.intervals || []);
    });

    return WEEK_DAY_ORDER.map(({ id }) => {
        const intervals = map.get(id) ?? [];
        return {
            dayId: id,
            intervals,
            isDayOff: intervals.length === 0,
        };
    });
};

export const formatIntervals = (intervals: WorkingIntervalDto[]): string => {
    if (!intervals.length) {
        return '';
    }
    return intervals
        .map((interval) => `${interval.opensAt} — ${interval.closesAt}`)
        .join(', ');
};

