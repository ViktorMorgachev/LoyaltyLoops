import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { CssBaseline } from '@mui/material';

// Страницы
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage'; // Диспетчер
import { ProfilePage } from './pages/ProfilePage';

// Партнер
import { PartnerDashboardPage } from './pages/partner/PartnerDashboardPage';
import { PointsPage } from './pages/partner/PointsPage';
import { PointDetailsPage } from './pages/partner/PointDetailsPage';
import { CreateBusinessPage } from './pages/partner/CreateBusinessPage';
import { BusinessSettingsPage } from './pages/partner/BusinessSettingsPage';
import { TransactionsPage } from './pages/partner/TransactionsPage';
import { PartnerStaffPage } from './pages/partner/PartnerStaffPage'; // NEW

// Админ
import { AllPartnersPage } from './pages/admin/AllPartnersPage';
import { PartnerDetailsAdminPage } from './pages/admin/PartnerDetailsAdminPage';

// Лейаут
import { MainLayout } from './components/MainLayout';
import { SelectRolePage } from './pages/SelectRolePage';

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
            <Route path="/select-role" element={<SelectRolePage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />

            {/* Партнер */}
            <Route path="/partner/dashboard" element={<PartnerDashboardPage />} />
            <Route path="/partner/points" element={<PointsPage />} />
            <Route path="/partner/points/:id" element={<PointDetailsPage />} />
            <Route path="/partner/transactions" element={<TransactionsPage />} />
            <Route path="/partner/onboarding" element={<CreateBusinessPage />} />
            <Route path="/partner/settings" element={<BusinessSettingsPage />} />
            <Route path="/partner/staff" element={<PartnerStaffPage />} />

            {/* Супер-Админ */}
            <Route path="/admin/partners" element={<AllPartnersPage />} />
            <Route path="/admin/partners/:id" element={<PartnerDetailsAdminPage />} />
          </Route>

          {/* Фоллбэк */}
          <Route path="*" element={<Navigate to="/login" />} />
        </Routes>
      </BrowserRouter>
    </>
  );
}

export default App;
