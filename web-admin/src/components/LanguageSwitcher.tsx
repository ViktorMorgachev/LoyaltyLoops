import React, { useState } from 'react';
import { Button, Menu, MenuItem } from '@mui/material';
import LanguageIcon from '@mui/icons-material/Language';
import { useTranslation } from 'react-i18next';
import { api } from '../api/axiosConfig';
import { useNotification } from '../context/NotificationContext';
import { getErrorMessage } from '../utils/errorHandler';
import { useUser } from '../context/UserContext';

const languages = [
  { code: 'ru', label: 'Русский' },
  { code: 'en', label: 'English' },
  { code: 'ky', label: 'Кыргызча' },
  { code: 'kk', label: 'Қазақша' },
  { code: 'uz', label: "O'zbekcha" },
  { code: 'be', label: 'Беларуская' },
];

export const LanguageSwitcher = () => {
  const { i18n } = useTranslation();
  const { showSuccess, showError } = useNotification();
  const { refreshUser } = useUser();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [saving, setSaving] = useState(false);

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const changeLanguage = async (lang: string) => {
    if (saving) return;
    setSaving(true);
    try {
        const hasToken = Boolean(localStorage.getItem('accessToken'));
        if (hasToken) {
            await api.post('/client/language', { language: lang });
            showSuccess(i18n.t('profile.language_saved'));
            refreshUser();
        }

        i18n.changeLanguage(lang);
        api.defaults.headers.common['Accept-Language'] = lang;
        localStorage.setItem('lang', lang);
    } catch (error: any) {
        showError(getErrorMessage(error));
    } finally {
        setSaving(false);
        setAnchorEl(null);
    }
  };

  return (
    <>
      <Button color="inherit" onClick={handleClick} startIcon={<LanguageIcon />}>
        {i18n.language.toUpperCase().substring(0, 2)}
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
      >
        {languages.map((lang) => (
          <MenuItem
            key={lang.code}
            onClick={() => changeLanguage(lang.code)}
            selected={i18n.language.startsWith(lang.code)}
            disabled={saving}
          >
            {lang.label}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
};