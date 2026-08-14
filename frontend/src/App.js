import React, { useState, useEffect } from 'react';
import './App.css';
import EventForm from './components/EventForm';
import EventList from './components/EventList';
import ChainVerification from './components/ChainVerification';

function App() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('events');

  useEffect(() => {
    fetchEvents();
  }, []);

  const fetchEvents = async () => {
    setLoading(true);
    try {
      const response = await fetch('/audit/events?limit=50');
      const data = await response.json();
      setEvents(data.records || []);
    } catch (error) {
      console.error('Error fetching events:', error);
    } finally {
      setLoading(false);
    }
  };

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
          📋 Events
        </button>
        <button 
          className={`nav-btn ${activeTab === 'verify' ? 'active' : ''}`}
          onClick={() => setActiveTab('verify')}
        >
          ✓ Verify Chain
        </button>
        <button 
          className={`nav-btn ${activeTab === 'create' ? 'active' : ''}`}
          onClick={() => setActiveTab('create')}
        >
          ➕ Create Event
        </button>
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
      </main>
    </div>
  );
}

export default App;
