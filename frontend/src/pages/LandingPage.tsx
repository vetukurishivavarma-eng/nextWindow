import { Link } from 'react-router-dom';
import { Icon } from '../components/Icon';
import { useAuth } from '../hooks/useAuth';

export function LandingPage() {
  const { user } = useAuth();
  return (
    <>
      <section className="hero container">
        <span className="eyebrow"><Icon name="shield" className="icon" /> Free, open-source, no AI required</span>
        <h1>Stop checking.<br /><span className="accent">Start getting notified.</span></h1>
        <p className="lead">
          NextWindow watches web pages for the changes you care about — price drops, restocks, keywords and meaningful
          updates — and tells you the moment they happen.
        </p>
        <div className="row" style={{ justifyContent: 'center' }}>
          <Link to={user ? '/watches/new' : '/register'} className="btn btn-primary btn-lg">{user ? 'Create a watch' : 'Start watching — free'}</Link>
          {!user && <Link to="/login" className="btn btn-lg">Sign in</Link>}
        </div>
      </section>

      <section className="container">
        <div className="card preview-card" aria-label="Example notification">
          <div className="row-between">
            <div className="row"><span className="dot" /><strong>Price dropped: Aurora Wireless Headphones</strong></div>
            <span className="tiny muted">example</span>
          </div>
          <p className="text-2 small" style={{ margin: '8px 0 10px 20px' }}>Price fell from $199.00 to $169.15 (−15.0%).</p>
          <div className="value-change" style={{ marginLeft: 20 }}><span className="old">$199.00</span><span className="muted">→</span><span className="new">$169.15</span></div>
        </div>

        <div className="feature-grid">
          {[
            ['tag', 'Prices & targets', 'Get alerted on any price drop, or only when it reaches the price you want to pay.'],
            ['box', 'Back in stock', 'Know when sold-out items become available — no more refreshing.'],
            ['search', 'Keywords & changes', 'Watch for words appearing or disappearing, or any meaningful page change.'],
          ].map(([icon, title, text]) => (
            <div key={title} className="card">
              <div className="feature-icon"><Icon name={icon} /></div>
              <h3>{title}</h3>
              <p className="muted small" style={{ marginTop: 6 }}>{text}</p>
            </div>
          ))}
        </div>

        <h2 style={{ margin: '40px 0 16px', textAlign: 'center' }}>How it works</h2>
        <div className="steps">
          <div className="card step"><h3>Paste a link</h3><p className="muted small" style={{ marginTop: 6 }}>We analyze the page and show what can be tracked: title, price, availability.</p></div>
          <div className="card step"><h3>Choose a condition</h3><p className="muted small" style={{ marginTop: 6 }}>Price drop, target price, restock, keyword or any change — once or every time.</p></div>
          <div className="card step"><h3>Get notified</h3><p className="muted small" style={{ marginTop: 6 }}>Alerts arrive in-app and as browser push notifications, without duplicates.</p></div>
        </div>

        <div className="card" style={{ marginTop: 32 }}>
          <div className="row" style={{ alignItems: 'flex-start' }}>
            <div className="feature-icon" style={{ marginBottom: 0 }}><Icon name="shield" /></div>
            <div className="grow">
              <h3>Responsible by design</h3>
              <p className="muted small" style={{ marginTop: 6 }}>
                NextWindow identifies itself, honours robots.txt and rate limits, and never bypasses logins, paywalls,
                CAPTCHAs or bot protection. Popular pages are fetched once and shared across everyone watching them.
              </p>
            </div>
          </div>
        </div>
      </section>
      <footer className="footer" style={{ marginTop: 48 }}>
        <div className="container row-between"><span>© NextWindow</span><span>Deterministic monitoring · no paid APIs</span></div>
      </footer>
    </>
  );
}
