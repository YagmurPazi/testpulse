import { Link, Outlet } from 'react-router';
import styles from './Layout.module.css';

export function Layout() {
  return (
    <>
      <a className={styles.skipLink} href="#main">
        Skip to content
      </a>
      <header className={styles.header}>
        <Link to="/" className={styles.brand}>
          <img src="/favicon.svg" alt="" width={24} height={24} />
          TestPulse
        </Link>
        <span className={styles.tagline}>Flaky test detection</span>
      </header>
      <main id="main" className={styles.main}>
        <Outlet />
      </main>
    </>
  );
}
