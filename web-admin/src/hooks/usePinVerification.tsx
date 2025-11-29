import React, { useCallback, useMemo, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Typography,
  Alert
} from '@mui/material';
import type { Workspace } from '../context/UserContext';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from 'react-i18next';
import { api } from '../api/axiosConfig';
import { getErrorMessage } from '../utils/errorHandler';

const PIN_BYPASS_ROLES = new Set(['PLATFORM_SUPER_ADMIN', 'PLATFORM_MANAGER']);

export const usePinVerification = (
  onVerified: (workspace: Workspace) => void
) => {
  const { showError, showSuccess } = useNotification();
  const { t } = useTranslation();

  const [dialogWorkspace, setDialogWorkspace] = useState<Workspace | null>(null);
  const [pinValue, setPinValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [resetLoading, setResetLoading] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const closeDialog = useCallback(() => {
    if (loading) return;
    setDialogWorkspace(null);
    setPinValue('');
    setFormError(null);
  }, [loading]);

  const requestPinVerification = useCallback(
    (workspace: Workspace) => {
      const bypass = PIN_BYPASS_ROLES.has(workspace.role);
      const shouldVerify = workspace.requirePin && !bypass;
      if (!shouldVerify) {
        onVerified(workspace);
        return;
      }

      setDialogWorkspace(workspace);
      setPinValue('');
      setFormError(null);
    },
    [onVerified]
  );

  const handleSubmit = useCallback(async () => {
    if (!dialogWorkspace) return;
    const trimmed = pinValue.trim();
    if (trimmed.length < 4) {
      setFormError(t('profile.pin_modal_input_error'));
      return;
    }
    setLoading(true);
    try {
      await api.post('/auth/verify-pin', {
        workspaceId: dialogWorkspace.id,
        pin: trimmed
      });
      showSuccess(t('profile.pin_modal_success'));
      const target = dialogWorkspace;
      setDialogWorkspace(null);
      setPinValue('');
      setFormError(null);
      onVerified(target);
    } catch (error) {
      const message = getErrorMessage(error);
      setFormError(message);
      showError(message);
    } finally {
      setLoading(false);
    }
  }, [
    dialogWorkspace,
    pinValue,
    showError,
    showSuccess,
    t,
    onVerified
  ]);

  const handleSendReset = useCallback(async () => {
    setResetLoading(true);
    try {
      await api.post('/partners/pin/reset/request');
      showSuccess(t('profile.pin_reset_email_sent'));
    } catch (error) {
      showError(getErrorMessage(error));
    } finally {
      setResetLoading(false);
    }
  }, [showError, showSuccess, t]);

  const dialog = useMemo(() => {
    const open = Boolean(dialogWorkspace);
    return (
      <Dialog
        open={open}
        onClose={closeDialog}
        PaperProps={{
          component: 'form',
          onSubmit: (event: React.FormEvent<HTMLFormElement>) => {
            event.preventDefault();
            handleSubmit();
          }
        }}
      >
        <DialogTitle>
          {t('profile.pin_modal_title', {
            name: dialogWorkspace?.title ?? ''
          })}
        </DialogTitle>
        <DialogContent sx={{ minWidth: 360, pt: 2 }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('profile.pin_modal_desc')}
          </Typography>
          <TextField
            autoFocus
            fullWidth
            type="password"
            autoComplete="one-time-code"
            label={t('profile.pin_modal_placeholder')}
            value={pinValue}
            inputProps={{ maxLength: 12, inputMode: 'numeric' }}
            onChange={(e) => {
              const digitsOnly = e.target.value.replace(/\D+/g, '');
              setPinValue(digitsOnly);
            }}
            disabled={loading}
          />
          {formError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {formError}
            </Alert>
          )}
          <Button
            variant="text"
            sx={{ mt: 2, px: 0 }}
            onClick={handleSendReset}
            disabled={resetLoading}
          >
            {resetLoading ? t('common.loading') : t('profile.pin_modal_reset')}
          </Button>
          <Typography variant="caption" color="text.secondary" display="block">
            {t('profile.pin_modal_reset_hint')}
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={closeDialog} disabled={loading}>
            {t('profile.pin_modal_cancel')}
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={loading || pinValue.trim().length < 4}
          >
            {loading ? t('common.loading') : t('profile.pin_modal_confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    );
  }, [
    closeDialog,
    dialogWorkspace,
    formError,
    handleSendReset,
    handleSubmit,
    loading,
    pinValue,
    resetLoading,
    t
  ]);

  return {
    requestPinVerification,
    PinDialog: dialog
  };
};


