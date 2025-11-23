import React from 'react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';
import type { RevenueChartPoint } from '../../types/analytics';
import { useTheme } from '@mui/material';

interface Props {
    data: RevenueChartPoint[];
}

export const RevenueChart: React.FC<Props> = ({ data }) => {
    const theme = useTheme();

    return (
        <div style={{ width: '100%', height: 300 }}>
            <ResponsiveContainer>
                <AreaChart
                    data={data}
                    margin={{
                        top: 10,
                        right: 30,
                        left: 0,
                        bottom: 0,
                    }}
                >
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e0e0e0" />
                    <XAxis 
                        dataKey="date" 
                        tick={{ fontSize: 12, fill: theme.palette.text.secondary }}
                        tickFormatter={(value: string) => value.length > 5 ? value.slice(5) : value} 
                        stroke={theme.palette.divider}
                    />
                    <YAxis 
                        tick={{ fontSize: 12, fill: theme.palette.text.secondary }}
                        stroke={theme.palette.divider}
                    />
                    <Tooltip 
                        contentStyle={{ 
                            backgroundColor: theme.palette.background.paper, 
                            border: `1px solid ${theme.palette.divider}`, 
                            borderRadius: 8,
                            boxShadow: theme.shadows[2]
                        }}
                    />
                    <Area 
                        type="monotone" 
                        dataKey="revenue" 
                        stroke={theme.palette.primary.main} 
                        fill={theme.palette.primary.main} 
                        fillOpacity={0.1}
                        strokeWidth={2}
                        name="Revenue"
                        activeDot={{ r: 6 }}
                    />
                </AreaChart>
            </ResponsiveContainer>
        </div>
    );
};

