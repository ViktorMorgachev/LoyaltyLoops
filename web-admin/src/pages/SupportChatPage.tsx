import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Typography,
  Badge
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { api, WS_BASE_URL } from '../api/axiosConfig';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { useUser } from '../context/UserContext';

const useBrowserNotifier = () => {
  const [permission, setPermission] = useState<
    NotificationPermission | 'unsupported'
  >(() => {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      return 'unsupported';
    }
    return Notification.permission;
  });

  useEffect(() => {
    if (permission !== 'default') return;
    if (typeof window === 'undefined' || !('Notification' in window)) return;
    Notification.requestPermission().then(setPermission);
  }, [permission]);

  return useCallback(
    (title: string, body: string) => {
      if (typeof window === 'undefined' || !('Notification' in window)) {
        return;
      }
      if (permission !== 'granted') return;
      if (document.visibilityState === 'visible') return;
      new Notification(title, { body });
    },
    [permission]
  );
};

type Mode = 'partner' | 'admin';

interface SupportThread {
  id: string;
  partnerId: string;
  partnerName: string;
  lastMessageSnippet?: string | null;
  lastMessageAt?: number | null;
  unreadForPartner: number;
  unreadForAdmin: number;
}

interface SupportMessage {
  id: string;
  threadId: string;
  senderId: string;
  senderRole: string;
  content: string;
  createdAt: number;
  isFromPartner: boolean;
}

interface SupportThreadResponse {
  thread: SupportThread;
  messages: SupportMessage[];
}

interface SupportChatEvent {
  type: 'THREAD_UPDATED' | 'MESSAGE_CREATED';
  thread?: SupportThread;
  message?: SupportMessage;
}

interface SupportChatPageProps {
  mode?: Mode;
}

const formatTimestamp = (ts?: number | null) => {
  if (!ts) return '—';
  return new Date(ts).toLocaleString();
};

const sortMessages = (messages: SupportMessage[]) =>
  [...messages].sort((a, b) => Number(a.createdAt) - Number(b.createdAt));

export const SupportChatPage: React.FC<SupportChatPageProps> = ({ mode }) => {
  const { isPartnerAdmin, isPartner, isPlatformStaff } = useUser();
  const effectiveMode: Mode = mode ?? (isPlatformStaff ? 'admin' : 'partner');

  if (effectiveMode === 'admin') {
    return <AdminSupportChat />;
  }

  if (!isPartner || !isPartnerAdmin) {
    return (
      <Paper sx={{ p: 3 }}>
        <Typography>{'Недостаточно прав для просмотра чата'}</Typography>
      </Paper>
    );
  }

  return <PartnerSupportChat />;
};

const PartnerSupportChat: React.FC = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();
  const notify = useBrowserNotifier();
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  const fetchThread = useCallback(async () => {
    try {
      const { data } = await api.get<SupportThreadResponse>('/partners/support/thread');
      setMessages(sortMessages(data.messages));
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [showError]);

  useEffect(() => {
    fetchThread();
  }, [fetchThread]);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const socket = new WebSocket(`${WS_BASE_URL}/ws/support/partner?token=${token}`);
    socket.onmessage = (event) => {
      const payload: SupportChatEvent = JSON.parse(event.data);
      if (payload.message) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === payload.message!.id)) {
            return prev;
          }
          return sortMessages([...prev, payload.message!]);
        });
        if (!payload.message.isFromPartner) {
          notify(t('support.partner_title'), payload.message.content);
        }
      }
    };
    socket.onerror = () => socket.close();
    return () => socket.close();
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text) {
      showError(t('support.validation_empty'));
      return;
    }
    setSending(true);
    try {
      await api.post('/partners/support/messages', { content: text });
      setInput('');
      showSuccess(t('support.sent'));
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setSending(false);
    }
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" mt={4}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Paper
      elevation={0}
      sx={{
        p: 0,
        width: '100%',
        maxWidth: 1000,
        mx: 'auto',
        borderRadius: 4,
        border: '1px solid',
        borderColor: 'divider',
        overflow: 'hidden',
        height: 'calc(100vh - 120px)',
        display: 'flex',
        flexDirection: 'column'
      }}
    >
      <Box p={3} borderBottom="1px solid" borderColor="divider" bgcolor="grey.50">
          <Typography variant="h5" fontWeight="bold" gutterBottom sx={{ background: 'linear-gradient(45deg, #2563eb 30%, #ec4899 90%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            {t('support.partner_title')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('support.partner_hint')}
          </Typography>
      </Box>

      <Box
        sx={{
          flex: 1,
          overflowY: 'auto',
          bgcolor: '#ffffff',
          p: 3,
          display: 'flex', 
          flexDirection: 'column', 
          gap: 1
        }}
      >
        {messages.length === 0 && (
          <Box display="flex" height="100%" alignItems="center" justifyContent="center">
              <Typography color="text.secondary">{t('support.empty_state')}</Typography>
          </Box>
        )}
        {messages.map((msg) => (
          <MessageBubble key={msg.id} message={msg} isMine={!msg.isFromPartner} />
        ))}
        <div ref={bottomRef} />
      </Box>

      <Box p={2} borderTop="1px solid" borderColor="divider" bgcolor="grey.50">
          <Stack direction="row" spacing={2}>
            <TextField
              fullWidth
              size="small"
              placeholder={t('support.placeholder')}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              sx={{ bgcolor: 'white', borderRadius: 1 }}
              onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      handleSend();
                  }
              }}
            />
            <Button variant="contained" onClick={handleSend} disabled={sending} sx={{ borderRadius: 2, px: 3 }}>
              {sending ? <CircularProgress size={24} color="inherit" /> : t('support.send')}
            </Button>
          </Stack>
      </Box>
    </Paper>
  );
};

const AdminSupportChat: React.FC = () => {
  const { t } = useTranslation();
  const { showError, showSuccess } = useNotification();
  const notify = useBrowserNotifier();
  const [threads, setThreads] = useState<SupportThread[]>([]);
  const [selected, setSelected] = useState<SupportThread | null>(null);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  const loadThreads = useCallback(async () => {
    try {
      const { data } = await api.get<SupportThread[]>('/admin/support/threads');
      setThreads(data);
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [showError]);

  const loadThreadDetails = useCallback(
    async (threadId: string) => {
      try {
        const { data } = await api.get<SupportThreadResponse>(`/admin/support/threads/${threadId}`);
        setSelected(data.thread);
        setMessages(sortMessages(data.messages));
      } catch (e: any) {
        showError(getErrorMessage(e));
      }
    },
    [showError]
  );

  useEffect(() => {
    loadThreads();
  }, [loadThreads]);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const socket = new WebSocket(`${WS_BASE_URL}/ws/support/admin?token=${token}`);
    socket.onmessage = (event) => {
      const payload: SupportChatEvent = JSON.parse(event.data);
      if (payload.thread) {
        setThreads((prev) => {
          const next = [...prev];
          const idx = next.findIndex((t) => t.id === payload.thread!.id);
          if (idx >= 0) {
            next[idx] = payload.thread!;
          } else {
            next.unshift(payload.thread!);
          }
          return next;
        });
      }
      if (payload.message && selected && payload.message.threadId === selected.id) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === payload.message!.id)) {
            return prev;
          }
          return sortMessages([...prev, payload.message!]);
        });
      }
      if (payload.message?.isFromPartner) {
        const title =
          payload.thread?.partnerName ||
          selected?.partnerName ||
          t('support.admin_title');
        notify(title, payload.message.content);
      }
    };
    socket.onerror = () => socket.close();
    return () => socket.close();
  }, [selected]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    if (!selected) return;
    const text = input.trim();
    if (!text) {
      showError(t('support.validation_empty'));
      return;
    }
    setSending(true);
    try {
      await api.post(`/admin/support/threads/${selected.id}/messages`, { content: text });
      setInput('');
      showSuccess(t('support.sent'));
    } catch (e: any) {
      showError(getErrorMessage(e));
    } finally {
      setSending(false);
    }
  };

  const sortedThreads = useMemo(
    () =>
      [...threads].sort((a, b) => Number(b.lastMessageAt || 0) - Number(a.lastMessageAt || 0)),
    [threads]
  );

  return (
    <Box display="flex" flexDirection={{ xs: 'column', md: 'row' }} gap={3} sx={{ height: 'calc(100vh - 100px)' }}>
      <Paper elevation={0} sx={{ width: { xs: '100%', md: 360 }, flexShrink: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column', borderRadius: 4, border: '1px solid', borderColor: 'divider' }}>
          <Box p={3} borderBottom="1px solid" borderColor="divider" bgcolor="grey.50">
            <Typography variant="h6" fontWeight="bold">
                {t('support.admin_title')}
            </Typography>
          </Box>
          
          {loading && (
            <Box display="flex" justifyContent="center" mt={2}>
              <CircularProgress size={24} />
            </Box>
          )}
          
          <List sx={{ overflowY: 'auto', flex: 1, p: 1 }}>
            {sortedThreads.map((thread) => (
                <ListItemButton
                  key={thread.id}
                  selected={selected?.id === thread.id}
                  onClick={() => loadThreadDetails(thread.id)}
                  sx={{ 
                      borderRadius: 2, 
                      mb: 0.5,
                      '&.Mui-selected': { bgcolor: 'primary.50', color: 'primary.main' }
                  }}
                >
                <ListItemText
                  primary={
                    <Stack
                      direction="row"
                      justifyContent="space-between"
                      alignItems="center"
                      spacing={1}
                      sx={{ width: '100%' }}
                    >
                      <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'inherit' }}>
                        {thread.partnerName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatTimestamp(thread.lastMessageAt).split(',')[0]}
                      </Typography>
                    </Stack>
                  }
                  secondary={
                    <Box display="flex" justifyContent="space-between" alignItems="center" mt={0.5}>
                        <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: 180 }}>
                          {thread.lastMessageSnippet || t('support.no_messages')}
                        </Typography>
                        {thread.unreadForAdmin > 0 && (
                            <Badge badgeContent={thread.unreadForAdmin} color="error" sx={{ '& .MuiBadge-badge': { fontSize: '0.7rem', height: 18, minWidth: 18 } }} />
                        )}
                    </Box>
                  }
                />
                </ListItemButton>
            ))}
            {sortedThreads.length === 0 && (
              <Box p={3} textAlign="center">
                  <Typography variant="body2" color="text.secondary">
                    {t('support.no_threads')}
                  </Typography>
              </Box>
            )}
          </List>
      </Paper>

      <Paper elevation={0} sx={{ flex: 1, display: 'flex', flexDirection: 'column', borderRadius: 4, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
          {selected ? (
            <>
              <Box p={2} px={3} borderBottom="1px solid" borderColor="divider" bgcolor="grey.50" display="flex" justifyContent="space-between" alignItems="center">
                  <Box>
                      <Typography variant="h6" fontWeight="bold">{selected.partnerName}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        ID: {selected.id}
                      </Typography>
                  </Box>
              </Box>
              
              <Box sx={{ flexGrow: 1, overflowY: 'auto', bgcolor: '#ffffff', p: 3, display: 'flex', flexDirection: 'column', gap: 1 }}>
                {messages.length === 0 && (
                  <Box display="flex" height="100%" alignItems="center" justifyContent="center">
                      <Typography color="text.secondary">{t('support.empty_state')}</Typography>
                  </Box>
                )}
                {messages.map((msg) => (
                  <MessageBubble key={msg.id} message={msg} isMine={!msg.isFromPartner} />
                ))}
                <div ref={bottomRef} />
              </Box>
              
              <Box p={2} borderTop="1px solid" borderColor="divider" bgcolor="grey.50">
                  <Stack direction="row" spacing={2}>
                    <TextField
                      fullWidth
                      size="small"
                      placeholder={t('support.placeholder')}
                      value={input}
                      onChange={(e) => setInput(e.target.value)}
                      sx={{ bgcolor: 'white', borderRadius: 1 }}
                      onKeyDown={(e) => {
                          if (e.key === 'Enter' && !e.shiftKey) {
                              e.preventDefault();
                              handleSend();
                          }
                      }}
                    />
                    <Button variant="contained" onClick={handleSend} disabled={sending} sx={{ borderRadius: 2, px: 3 }}>
                      {sending ? <CircularProgress size={24} color="inherit" /> : t('support.send')}
                    </Button>
                  </Stack>
              </Box>
            </>
          ) : (
            <Box flex={1} display="flex" alignItems="center" justifyContent="center" flexDirection="column" gap={2}>
              <Box sx={{ p: 3, bgcolor: 'grey.100', borderRadius: '50%' }}>
                  {/* Icon placeholder */}
                  <Typography variant="h4">💬</Typography>
              </Box>
              <Typography color="text.secondary" fontWeight="500">{t('support.select_thread')}</Typography>
            </Box>
          )}
      </Paper>
    </Box>
  );
};

const MessageBubble = ({ message, isMine }: { message: SupportMessage; isMine: boolean }) => {
  const align = isMine ? 'flex-end' : 'flex-start';
  const bgcolor = isMine ? 'primary.main' : 'grey.100';
  const textColor = isMine ? 'white' : 'text.primary';
  const borderRadius = isMine ? '20px 20px 0 20px' : '20px 20px 20px 0';

  return (
    <Box display="flex" justifyContent={align} mb={1}>
      <Box
        sx={{
          bgcolor,
          color: textColor,
          px: 2.5,
          py: 1.5,
          borderRadius,
          maxWidth: '75%',
          boxShadow: '0 1px 2px rgba(0,0,0,0.1)'
        }}
      >
        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
          {message.content}
        </Typography>
        <Typography variant="caption" display="block" textAlign="right" sx={{ mt: 0.5, opacity: 0.8, fontSize: '0.7rem' }}>
          {new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        </Typography>
      </Box>
    </Box>
  );
};

