import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Divider,
  Grid,
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
  const [thread, setThread] = useState<SupportThread | null>(null);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  const fetchThread = useCallback(async () => {
    try {
      const { data } = await api.get<SupportThreadResponse>('/partners/support/thread');
      setThread(data.thread);
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
      if (payload.thread) {
        setThread(payload.thread);
      }
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
    <Paper sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>{t('support.partner_title')}</Typography>
      <Typography variant="body2" color="text.secondary" mb={3}>
        {t('support.partner_hint')}
      </Typography>

      <Box
        sx={{
          height: 420,
          overflowY: 'auto',
          bgcolor: '#f8f9fb',
          borderRadius: 2,
          p: 2,
          mb: 2
        }}
      >
        {messages.length === 0 && (
          <Typography color="text.secondary">{t('support.empty_state')}</Typography>
        )}
        {messages.map((msg) => (
          <MessageBubble key={msg.id} message={msg} isMine={!msg.isFromPartner} />
        ))}
        <div ref={bottomRef} />
      </Box>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField
          fullWidth
          multiline
          maxRows={4}
          placeholder={t('support.placeholder')}
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <Button variant="contained" onClick={handleSend} disabled={sending}>
          {sending ? t('common.loading') : t('support.send')}
        </Button>
      </Stack>
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
    <Grid container spacing={3}>
      <Grid item xs={12} md={4}>
        <Paper sx={{ p: 2, height: '100%', overflow: 'hidden' }}>
          <Typography variant="h6" gutterBottom>
            {t('support.admin_title')}
          </Typography>
          {loading && (
            <Box display="flex" justifyContent="center" mt={2}>
              <CircularProgress size={24} />
            </Box>
          )}
          <List sx={{ maxHeight: 520, overflowY: 'auto' }}>
            {sortedThreads.map((thread) => (
              <React.Fragment key={thread.id}>
                <ListItemButton
                  selected={selected?.id === thread.id}
                  onClick={() => loadThreadDetails(thread.id)}
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
                      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                        {thread.partnerName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatTimestamp(thread.lastMessageAt)}
                      </Typography>
                    </Stack>
                  }
                  secondary={
                    thread.unreadForAdmin > 0 ? (
                      <Badge color="error" badgeContent={thread.unreadForAdmin}>
                        <Typography variant="body2" color="text.secondary">
                          {thread.lastMessageSnippet || t('support.no_messages')}
                        </Typography>
                      </Badge>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        {thread.lastMessageSnippet || t('support.no_messages')}
                      </Typography>
                    )
                  }
                />
                </ListItemButton>
                <Divider component="li" />
              </React.Fragment>
            ))}
            {sortedThreads.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                {t('support.no_threads')}
              </Typography>
            )}
          </List>
        </Paper>
      </Grid>

      <Grid item xs={12} md={8}>
        <Paper sx={{ p: 3, minHeight: 600, display: 'flex', flexDirection: 'column' }}>
          {selected ? (
            <>
              <Typography variant="h6">{selected.partnerName}</Typography>
              <Typography variant="body2" color="text.secondary" mb={2}>
                {t('support.thread_id', { id: selected.id })}
              </Typography>
              <Box sx={{ flexGrow: 1, overflowY: 'auto', bgcolor: '#f8f9fb', borderRadius: 2, p: 2 }}>
                {messages.length === 0 && (
                  <Typography color="text.secondary">{t('support.empty_state')}</Typography>
                )}
                {messages.map((msg) => (
                  <MessageBubble key={msg.id} message={msg} isMine={!msg.isFromPartner} />
                ))}
                <div ref={bottomRef} />
              </Box>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} mt={2}>
                <TextField
                  fullWidth
                  multiline
                  maxRows={4}
                  placeholder={t('support.placeholder')}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                />
                <Button variant="contained" onClick={handleSend} disabled={sending}>
                  {sending ? t('common.loading') : t('support.send')}
                </Button>
              </Stack>
            </>
          ) : (
            <Box flex={1} display="flex" alignItems="center" justifyContent="center">
              <Typography color="text.secondary">{t('support.select_thread')}</Typography>
            </Box>
          )}
        </Paper>
      </Grid>
    </Grid>
  );
};

const MessageBubble = ({ message, isMine }: { message: SupportMessage; isMine: boolean }) => {
  const align = isMine ? 'flex-end' : 'flex-start';
  const color = isMine ? 'primary.main' : '#e0e0e0';
  const textColor = isMine ? 'white' : 'text.primary';

  return (
    <Box display="flex" justifyContent={align} mb={1.5}>
      <Box
        sx={{
          bgcolor: color,
          color: textColor,
          px: 2,
          py: 1,
          borderRadius: 2,
          maxWidth: '70%',
        }}
      >
        <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>
          {message.content}
        </Typography>
        <Typography variant="caption" display="block" textAlign="right">
          {new Date(message.createdAt).toLocaleString()}
        </Typography>
      </Box>
    </Box>
  );
};

