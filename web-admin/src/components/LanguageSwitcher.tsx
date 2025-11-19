import React, { useState } from 'react';
import { Button, Menu, MenuItem } from '@mui/material';
import LanguageIcon from '@mui/icons-material/Language';
import { useTranslation } from 'react-i18next';
import { api } from '../api/axiosConfig';

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
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const changeLanguage = (lang: string) => {
    i18n.changeLanguage(lang);
    api.defaults.headers['Accept-Language'] = lang; // Обновляем хедер для сервера
    setAnchorEl(null);
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
          >
            {lang.label}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
};