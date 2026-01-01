import { useCallback, useEffect, useState } from 'react';
import {
    Box,
    Paper,
    Typography,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Chip,
    TextField,
    MenuItem,
    Select,
    FormControl,
    InputLabel,
    Button,
    Stack,
    TablePagination,
    CircularProgress,
    Alert
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import RefreshIcon from '@mui/icons-material/Refresh';
import FilterListIcon from '@mui/icons-material/FilterList';
import { api } from '../../api/axiosConfig';
import { SystemEventType } from '../../types/events';
import type { SystemEvent } from '../../types/events';
import { useUser } from '../../context/UserContext';

const EVENT_TYPE_COLORS: Record<SystemEventType, "default" | "primary" | "secondary" | "error" | "info" | "success" | "warning"> = {
    [SystemEventType.LOGIN]: 'info',
    [SystemEventType.REGISTER]: 'success',
    [SystemEventType.SMS_REQUEST]: 'default',
    [SystemEventType.ACCRUAL]: 'success',
    [SystemEventType.REDEMPTION]: 'warning',
    [SystemEventType.TIER_CHANGE]: 'secondary',
    [SystemEventType.VISIT]: 'primary',
    [SystemEventType.ERROR]: 'error',
    [SystemEventType.INFO]: 'default',
    [SystemEventType.WARNING]: 'warning',
    [SystemEventType.OTP_VERIFICATION_FAILED]: 'warning',
    [SystemEventType.PIN_CHANGE_SUCCESS]: 'success',
    [SystemEventType.PIN_RESET_REQUEST]: 'info',
    [SystemEventType.PIN_RESET_SUCCESS]: 'success',
    [SystemEventType.PIN_VERIFICATION_FAILED]: 'error',
    [SystemEventType.USER_BANNED]: 'error'
};

export const SystemEventsPage = () => {
    const { t } = useTranslation();
    const [events, setEvents] = useState<SystemEvent[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const { isSuperAdmin, isSuperManager, currentWorkspace } = useUser();

    // Filters
    const [typeFilter, setTypeFilter] = useState<SystemEventType | 'ALL'>('ALL');
    const [phoneInput, setPhoneInput] = useState('');
    const [userIdInput, setUserIdInput] = useState('');
    const [phoneFilter, setPhoneFilter] = useState('');
    const [userIdFilter, setUserIdFilter] = useState('');
    const [dateFrom, setDateFrom] = useState('');
    const [dateTo, setDateTo] = useState('');

    // Debounce inputs
    useEffect(() => {
        const timer = setTimeout(() => {
            setPhoneFilter(phoneInput);
            setUserIdFilter(userIdInput);
        }, 500);
        return () => clearTimeout(timer);
    }, [phoneInput, userIdInput]);

    // Pagination
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(25);

    const fetchEvents = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = new URLSearchParams();
            if (typeFilter !== 'ALL') params.append('type', typeFilter);
            if (phoneFilter) params.append('userPhone', phoneFilter);
            if (userIdFilter) params.append('userId', userIdFilter);
            
            if (dateFrom) {
                params.append('from', new Date(dateFrom).getTime().toString());
            }
            if (dateTo) {
                params.append('to', new Date(dateTo).getTime().toString());
            }

            params.append('limit', rowsPerPage.toString());
            params.append('offset', (page * rowsPerPage).toString());

            const response = await api.get<SystemEvent[]>(`/admin/events?${params.toString()}`);
            setEvents(response.data);
        } catch (err) {
            console.error(err);
            setError(t('common.error'));
        } finally {
            setLoading(false);
        }
    }, [typeFilter, phoneFilter, userIdFilter, dateFrom, dateTo, page, rowsPerPage, t]);

    useEffect(() => {
        fetchEvents();
    }, [fetchEvents]);

    const handleFilterReset = () => {
        setTypeFilter('ALL');
        setPhoneInput('');
        setUserIdInput('');
        setPhoneFilter('');
        setUserIdFilter('');
        setDateFrom('');
        setDateTo('');
        setPage(0);
    };

    const hiddenTypes: SystemEventType[] = [
        SystemEventType.VISIT,
        SystemEventType.ACCRUAL,
        SystemEventType.REDEMPTION
    ];

    if (!currentWorkspace || (!isSuperAdmin && !isSuperManager)) {
        return null;
    }

    const visibleEvents = (isSuperAdmin || isSuperManager)
        ? events
        : events.filter((e) => !hiddenTypes.includes(e.type));

    return (
        <Box>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
                <Typography variant="h5" fontWeight={700}>
                    {t('system_events.title', { defaultValue: 'System Events' })}
                </Typography>
                <Button 
                    startIcon={<RefreshIcon />} 
                    onClick={() => fetchEvents()} 
                    variant="outlined"
                >
                    {t('system_events.filters.refresh', { defaultValue: 'Refresh' })}
                </Button>
            </Box>

            <Paper sx={{ p: 2, mb: 3 }}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems="center">
                    <FormControl size="small" sx={{ minWidth: 150 }}>
                        <InputLabel>{t('system_events.filters.type', { defaultValue: 'Event Type' })}</InputLabel>
                        <Select
                            value={typeFilter}
                            label={t('system_events.filters.type', { defaultValue: 'Event Type' })}
                            onChange={(e) => setTypeFilter(e.target.value as SystemEventType | 'ALL')}
                        >
                            <MenuItem value="ALL">{t('common.all', { defaultValue: 'All' })}</MenuItem>
                            {Object.values(SystemEventType).map((type) => (
                                <MenuItem key={type} value={type}>
                                    {t(`system_events.types.${type}`, { defaultValue: type })}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    <TextField
                        size="small"
                        label={t('system_events.filters.phone', { defaultValue: 'Phone' })}
                        value={phoneInput}
                        onChange={(e) => setPhoneInput(e.target.value)}
                    />

                    <TextField
                        size="small"
                        label={t('system_events.filters.user_id', { defaultValue: 'User ID' })}
                        value={userIdInput}
                        onChange={(e) => setUserIdInput(e.target.value)}
                    />

                    <TextField
                        size="small"
                        type="datetime-local"
                        label={t('system_events.filters.from', { defaultValue: 'From' })}
                        InputLabelProps={{ shrink: true }}
                        value={dateFrom}
                        onChange={(e) => setDateFrom(e.target.value)}
                    />

                    <TextField
                        size="small"
                        type="datetime-local"
                        label={t('system_events.filters.to', { defaultValue: 'To' })}
                        InputLabelProps={{ shrink: true }}
                        value={dateTo}
                        onChange={(e) => setDateTo(e.target.value)}
                    />

                    <Box flexGrow={1} />
                    
                    <Button 
                        startIcon={<FilterListIcon />} 
                        onClick={handleFilterReset}
                        color="inherit"
                    >
                        {t('system_events.filters.reset', { defaultValue: 'Reset' })}
                    </Button>
                </Stack>
            </Paper>

            {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

            <Paper sx={{ overflow: 'hidden' }}>
                <Box sx={{ overflowX: 'auto' }}>
                    <Table size="small">
                        <TableHead>
                            <TableRow sx={{ bgcolor: 'grey.50' }}>
                                <TableCell>{t('system_events.table.date', { defaultValue: 'Date' })}</TableCell>
                                <TableCell>{t('system_events.table.type', { defaultValue: 'Type' })}</TableCell>
                                <TableCell>{t('system_events.table.user', { defaultValue: 'User' })}</TableCell>
                                <TableCell>{t('system_events.table.details', { defaultValue: 'Details' })}</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {loading ? (
                                <TableRow>
                                    <TableCell colSpan={4} align="center" sx={{ py: 3 }}>
                                        <CircularProgress size={24} />
                                    </TableCell>
                                </TableRow>
                            ) : visibleEvents.length === 0 ? (
                                <TableRow>
                                    <TableCell colSpan={4} align="center" sx={{ py: 3 }}>
                                        <Typography color="text.secondary">
                                            {t('system_events.table.empty', { defaultValue: 'No events found' })}
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            ) : (
                                visibleEvents.map((event) => (
                                    <TableRow key={event.id} hover>
                                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                                            {new Date(event.timestamp).toLocaleString()}
                                        </TableCell>
                                        <TableCell>
                                            <Chip 
                                                label={t(`system_events.types.${event.type}`, { defaultValue: event.type })} 
                                                size="small" 
                                                color={EVENT_TYPE_COLORS[event.type] || 'default'} 
                                                variant="outlined"
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <Stack spacing={0.5}>
                                                {event.userPhone && (
                                                    <Typography variant="body2" fontWeight={500}>
                                                        {event.userPhone}
                                                    </Typography>
                                                )}
                                                {event.userId && (
                                                    <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                                        {event.userId}
                                                    </Typography>
                                                )}
                                            </Stack>
                                        </TableCell>
                                        <TableCell sx={{ maxWidth: 400 }}>
                                            <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                                                {event.payload || '-'}
                                            </Typography>
                                            {event.partnerId && (
                                                <Typography variant="caption" color="text.secondary" display="block" mt={0.5}>
                                                    Partner: {event.partnerId}
                                                </Typography>
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </Box>
                <TablePagination
                    component="div"
                    count={-1} // We don't know total count yet from API, simplified for now
                    page={page}
                    onPageChange={(_, newPage) => setPage(newPage)}
                    rowsPerPage={rowsPerPage}
                    onRowsPerPageChange={(e) => {
                        setRowsPerPage(parseInt(e.target.value, 10));
                        setPage(0);
                    }}
                    labelRowsPerPage={t('common.rows_per_page', { defaultValue: 'Rows:' })}
                />
            </Paper>
        </Box>
    );
};

