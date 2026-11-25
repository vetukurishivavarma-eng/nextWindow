import { Link, Outlet } from 'react-router-dom';
import { BrandMark } from '../components/Icon';
import { useAuth } from '../hooks/useAuth';

export function PublicLayout() {
  const { user } = useAuth();
  return (
    <>
      <header className="topbar">
        <div className="container topbar-inner">
          <Link to="/" className="brand"><BrandMark /> NextWindow</Link>
          <div className="topbar-actions">
            {user ? <Link to="/dashboard" className="btn btn-primary btn-sm">Open dashboard</Link> : (
              <>
                <Link to="/login" className="btn btn-ghost btn-sm">Sign in</Link>
                <Link to="/register" className="btn btn-primary btn-sm">Get started</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <Outlet />
    </>
  );
}
