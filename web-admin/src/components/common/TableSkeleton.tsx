import React from 'react';
import { TableRow, TableCell, Skeleton } from '@mui/material';

interface TableSkeletonProps {
    rows?: number;
    cols?: number;
    height?: number;
}

export const TableSkeleton: React.FC<TableSkeletonProps> = ({ rows = 5, cols = 4, height = 40 }) => {
    return (
        <>
            {Array.from({ length: rows }).map((_, rIdx) => (
                <TableRow key={`skel-row-${rIdx}`}>
                    {Array.from({ length: cols }).map((_, cIdx) => (
                        <TableCell key={`skel-cell-${rIdx}-${cIdx}`}>
                            <Skeleton variant="text" width={cIdx === 0 ? "80%" : "60%"} height={height} />
                        </TableCell>
                    ))}
                </TableRow>
            ))}
        </>
    );
};
