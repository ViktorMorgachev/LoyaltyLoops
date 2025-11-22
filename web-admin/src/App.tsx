import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { CssBaseline } from '@mui/material';

// Импортируем страницы (мы их сейчас создадим/обновим)
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage'; // Это Диспетчер
import { AllPartnersPage } from './pages/admin/AllPartnersPage';
import { PartnerDashboardPage } from './pages/partner/PartnerDashboardPage';
import { CreateBusinessPage } from './pages/partner/CreateBusinessPage';
import { ProfilePage } from './pages/ProfilePage';
import { MainLayout } from './components/MainLayout'; // Меню слева

function App() {
  return (
    <>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          {/* Публичная страница */}
          <Route path="/login" element={<LoginPage />} />

          {/* Защищенные страницы (внутри Меню) */}
          <Route element={<MainLayout />}>
            {/* Диспетчер (решает куда идти) */}
            <Route path="/dashboard" element={<DashboardPage />} />

            {/* Профиль */}
            <Route path="/profile" element={<ProfilePage />} />

            {/* Страницы Партнера */}
            <Route path="/partner/dashboard" element={<PartnerDashboardPage />} />
            <Route path="/partner/onboarding" element={<CreateBusinessPage />} />

            {/* Страницы Супер-Админа */}
            <Route path="/admin/partners" element={<AllPartnersPage />} />
          </Route>

          {/* Если страница не найдена -> на логин */}
          <Route path="*" element={<Navigate to="/login" />} />
        </Routes>
      </BrowserRouter>
    </>
  );
}

export default App;