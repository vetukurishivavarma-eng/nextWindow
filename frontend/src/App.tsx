import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useRef, type ReactNode } from 'react';
import { useAuth } from './hooks/useAuth';
import { AppLayout } from './layouts/AppLayout';
import { PublicLayout } from './layouts/PublicLayout';
import { Loading } from './components/States';
import { LandingPage } from './pages/LandingPage';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import { DashboardPage } from './pages/DashboardPage';
import { CreateWatchPage } from './pages/CreateWatchPage';
import { WatchDetailPage } from './pages/WatchDetailPage';
import { HistoryPage } from './pages/HistoryPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { SettingsPage } from './pages/SettingsPage';
import { NotFoundPage } from './pages/NotFoundPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth();
  const location = useLocation();
  if (!ready) return <Loading />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
}

/** Redirects visitors who arrive already signed in; a user who signs in on this page is navigated by the form itself. */
function GuestOnly({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth();
  const wasGuest = useRef(false);
  if (!ready) return <Loading />;
  if (!user) wasGuest.current = true;
  return user && !wasGuest.current ? <Navigate to="/dashboard" replace /> : <>{children}</>;
}

export function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
        <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      </Route>
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/watches/new" element={<CreateWatchPage />} />
        <Route path="/watches/:id" element={<WatchDetailPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
