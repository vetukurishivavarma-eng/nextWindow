import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { UnreadProvider, useUnread } from '../hooks/useUnread';
import { BrandMark, Icon } from '../components/Icon';

function Shell() {
  const { user, logout } = useAuth();
  const { unread } = useUnread();
  const navigate = useNavigate();
  const cls = ({ isActive }: { isActive: boolean }) => 'nav-link' + (isActive ? ' active' : '');
  const badge = unread > 0 ? <span className="nav-badge" aria-label={`${unread} unread`}>{unread > 99 ? '99+' : unread}</span> : null;
  return (
    <>
      <a href="#main" className="sr-only">Skip to content</a>
      <header className="topbar">
        <div className="container topbar-inner">
          <NavLink to="/dashboard" className="brand"><BrandMark /> NextWindow</NavLink>
          <nav className="nav-desktop" aria-label="Main">
            <NavLink to="/dashboard" className={cls}>Dashboard</NavLink>
            <NavLink to="/watches/new" className={cls}>New watch</NavLink>
            <NavLink to="/notifications" className={cls}>Notifications {badge}</NavLink>
            <NavLink to="/history" className={cls}>History</NavLink>
            <NavLink to="/settings" className={cls}>Settings</NavLink>
          </nav>
          <div className="topbar-actions">
            <span className="muted small truncate" style={{ maxWidth: 180 }} title={user?.email}>{user?.email}</span>
            <button className="btn btn-ghost btn-sm" onClick={async () => { await logout(); navigate('/'); }} aria-label="Sign out">
              <Icon name="logout" />
            </button>
          </div>
        </div>
      </header>
      <main id="main" className="main">
        <div className="container"><Outlet /></div>
      </main>
      <nav className="bottom-nav" aria-label="Main">
        <NavLink to="/dashboard"><Icon name="home" />Home</NavLink>
        <NavLink to="/notifications"><Icon name="bell" />Alerts{badge}</NavLink>
        <NavLink to="/watches/new" className={({ isActive }) => 'add' + (isActive ? ' active' : '')} aria-label="New watch"><Icon name="plus" /></NavLink>
        <NavLink to="/history"><Icon name="history" />History</NavLink>
        <NavLink to="/settings"><Icon name="settings" />Settings</NavLink>
      </nav>
    </>
  );
}

export function AppLayout() {
  return <UnreadProvider><Shell /></UnreadProvider>;
}
