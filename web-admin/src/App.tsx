import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { CssBaseline } from '@mui/material';

// Страницы
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage'; // Диспетчер
import { ProfilePage } from './pages/ProfilePage';

// Партнер
import { PartnerDashboardPage } from './pages/partner/PartnerDashboardPage';
import { CreateBusinessPage } from './pages/partner/CreateBusinessPage';
import { BusinessSettingsPage } from './pages/partner/BusinessSettingsPage'; // <-- Добавил

// Админ
import { AllPartnersPage } from './pages/admin/AllPartnersPage';

// Лейаут
import { MainLayout } from './components/MainLayout';

function App() {
  return (
    <>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          {/* Публичные */}
          <Route path="/login" element={<LoginPage />} />

          {/* Внутри Лейаута */}
          <Route element={<MainLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />

            {/* Партнер */}
            <Route path="/partner/dashboard" element={<PartnerDashboardPage />} />
            <Route path="/partner/onboarding" element={<CreateBusinessPage />} />
            <Route path="/partner/settings" element={<BusinessSettingsPage />} />

            {/* Супер-Админ */}
            <Route path="/admin/partners" element={<AllPartnersPage />} />
          </Route>

          {/* Фоллбэк */}
          <Route path="*" element={<Navigate to="/login" />} />
        </Routes>
      </BrowserRouter>
    </>
  );
}

export default App;