import { createTheme } from '@mui/material/styles';
import { alpha } from '@mui/material';

const primaryMain = '#436ee5'; // темнее для лучшего контраста
const textSecondary = '#374151';
const textDisabled = '#6b7280';

export const theme = createTheme({
  palette: {
    primary: {
      main: primaryMain,
      contrastText: '#ffffff',
    },
    text: {
      primary: '#111827',
      secondary: textSecondary,
      disabled: textDisabled,
    },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        outlined: {
          borderWidth: 1.5,
          borderColor: primaryMain,
          color: primaryMain,
          '&:hover': {
            borderWidth: 1.5,
            borderColor: '#1439a6',
            backgroundColor: alpha(primaryMain, 0.08),
          },
        },
      },
    },
    MuiTypography: {
      styleOverrides: {
        caption: {
          color: '#111827',
        },
        body2: {
          color: '#111827',
        },
      },
    },
  },
});

