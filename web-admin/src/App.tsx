import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { CssBaseline } from '@mui/material';

// Страницы
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProfilePage } from './pages/ProfilePage';
import { AboutPage } from './pages/AboutPage';
import { JoinPlatformManagerPage } from './pages/JoinPlatformManagerPage';
import { JoinPartnerManagerPage } from './pages/JoinPartnerManagerPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { PinResetPage } from './pages/PinResetPage';
import { SupportChatPage } from './pages/SupportChatPage';
import { TestLabPage } from './pages/TestLabPage';

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
          <Route path="/reset-pin" element={<PinResetPage />} />
          <Route path="/" element={<Navigate to="/profile" />} />

          {/* Внутри Лейаута */}
          <Route element={<MainLayout />}>
            <Route path="/select-role" element={<SelectRolePage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="/join/platform-manager" element={<JoinPlatformManagerPage />} />
            <Route path="/join/partner" element={<JoinPartnerManagerPage />} />
            <Route path="/test-lab" element={<TestLabPage />} />

            {/* Партнер */}
            <Route path="/partner/dashboard" element={<PartnerDashboardPage />} />
            <Route path="/partner/points" element={<PointsPage />} />
            <Route path="/partner/points/:id" element={<PointDetailsPage />} />
            <Route path="/partner/transactions" element={<TransactionsPage />} />
            <Route path="/partner/onboarding" element={<CreateBusinessPage />} />
            <Route path="/partner/settings" element={<BusinessSettingsPage />} />
            <Route path="/partner/staff" element={<PartnerStaffPage />} />
            <Route path="/partner/support" element={<SupportChatPage mode="partner" />} />

            {/* Супер-Админ */}
            <Route path="/admin/partners" element={<AllPartnersPage />} />
            <Route path="/admin/partners/:id" element={<PartnerDetailsAdminPage />} />
            <Route path="/admin/support" element={<SupportChatPage mode="admin" />} />
          </Route>

          {/* Фоллбэк */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </BrowserRouter>
    </>
  );
}

export default App;
