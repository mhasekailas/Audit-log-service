import React, { useState, useEffect } from 'react';
import './App.css';
import EventForm from './components/EventForm';
import EventList from './components/EventList';
import ChainVerification from './components/ChainVerification';
import ScenarioB from './components/ScenarioB';
import ScenarioC from './components/ScenarioC';
import { authorizedFetch, clearCredentials, getCredentials, setCredentials } from './api';

function App() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('events');
  const [credentials, setAuthState] = useState(getCredentials());
  const [login, setLogin] = useState({ username: '', password: '' });
  const [authError, setAuthError] = useState(null);

  useEffect(() => {
    fetchEvents();
  }, []);

  const fetchEvents = async (query = 'limit=50') => {
    setLoading(true);
    try {
      const response = await authorizedFetch(`/audit/events?${query}`);
      const data = await response.json();
      setEvents(data.records || []);
    } catch (error) {
      console.error('Error fetching events:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (event) => {
    event.preventDefault();
    setAuthState(btoa(`${login.username}:${login.password}`));
    setCredentials(login.username, login.password);
    setAuthError(null);
  };

  if (!credentials) {
    return (
      <div className="app">
        <header className="app-header"><h1>Audit Log Service</h1><p>Secure operator sign-in</p></header>
        <main className="app-main">
          <div className="form-container">
            <h2>Sign in to Audit APIs</h2>
            {authError && <div className="alert alert-error">{authError}</div>}
            <form onSubmit={handleLogin}>
              <div className="form-group"><label>Username</label><input value={login.username} onChange={(e) => setLogin({ ...login, username: e.target.value })} required /></div>
              <div className="form-group"><label>Password</label><input type="password" value={login.password} onChange={(e) => setLogin({ ...login, password: e.target.value })} required /></div>
              <button className="btn btn-primary">Sign in</button>
            </form>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>🔐 Audit Log Service</h1>
        <p>Tamper-evident event logging with hash chain verification</p>
      </header>

      <nav className="app-nav">
        <button 
          className={`nav-btn ${activeTab === 'events' ? 'active' : ''}`}
          onClick={() => setActiveTab('events')}
        >
          📋 Audit Events
        </button>
        <button 
          className={`nav-btn ${activeTab === 'verify' ? 'active' : ''}`}
          onClick={() => setActiveTab('verify')}
        >
          ✓ Chain Integrity
        </button>
        <button 
          className={`nav-btn ${activeTab === 'create' ? 'active' : ''}`}
          onClick={() => setActiveTab('create')}
        >
          ➕ Create Audit Event
        </button>
        <button
          className={`nav-btn ${activeTab === 'scenario-b' ? 'active' : ''}`}
          onClick={() => setActiveTab('scenario-b')}
        >
          ⚙ Retention & Redaction
        </button>
        <button
          className={`nav-btn ${activeTab === 'scenario-c' ? 'active' : ''}`}
          onClick={() => setActiveTab('scenario-c')}
        >
          📊 Compliance Reporting
        </button>
        <button className="nav-btn" onClick={() => { clearCredentials(); setAuthState(''); }}>Sign out</button>
      </nav>

      <main className="app-main">
        {activeTab === 'events' && (
          <EventList events={events} loading={loading} onRefresh={fetchEvents} />
        )}
        {activeTab === 'verify' && (
          <ChainVerification onVerify={fetchEvents} />
        )}
        {activeTab === 'create' && (
          <EventForm onEventCreated={() => {
            fetchEvents();
            setActiveTab('events');
          }} />
        )}
        {activeTab === 'scenario-b' && <ScenarioB />}
        {activeTab === 'scenario-c' && <ScenarioC />}
      </main>
    </div>
  );
}

export default App;
