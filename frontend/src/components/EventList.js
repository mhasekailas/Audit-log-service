import React, { useState } from 'react';
import { authorizedFetch } from '../api';

function EventList({ events, loading, onRefresh }) {
  const [filters, setFilters] = useState({
    actorId: '',
    eventType: '',
    resourceType: '',
    resourceId: ''
  });

  const handleFilterChange = (e) => {
    const { name, value } = e.target;
    setFilters(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const applyFilters = async () => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value) params.append(key, value);
    });
    
    try {
      const response = await authorizedFetch(`/audit/events?${params.toString()}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      await response.json();
      onRefresh(params.toString() || 'limit=50');
    } catch (error) {
      console.error('Error filtering events:', error);
    }
  };

  return (
    <div className="event-list-container">
      <h2>Audit Events</h2>

      <div className="filters-section">
        <h3>Filters</h3>
        <div className="filter-grid">
          <input 
            type="text" 
            name="actorId" 
            placeholder="Actor ID"
            value={filters.actorId}
            onChange={handleFilterChange}
          />
          <input 
            type="text" 
            name="eventType" 
            placeholder="Event Type"
            value={filters.eventType}
            onChange={handleFilterChange}
          />
          <input 
            type="text" 
            name="resourceType" 
            placeholder="Resource Type"
            value={filters.resourceType}
            onChange={handleFilterChange}
          />
          <input 
            type="text" 
            name="resourceId" 
            placeholder="Resource ID"
            value={filters.resourceId}
            onChange={handleFilterChange}
          />
        </div>
        <button className="btn btn-secondary" onClick={applyFilters}>Apply Filters</button>
        <button className="btn btn-secondary" onClick={onRefresh}>Refresh</button>
      </div>

      {loading ? (
        <div className="loading">Loading events...</div>
      ) : events.length === 0 ? (
        <div className="empty-state">No events found</div>
      ) : (
        <div className="events-table">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Event Type</th>
                <th>Actor ID</th>
                <th>Resource</th>
                <th>Timestamp</th>
                <th>Content Hash</th>
                <th>Chain Hash</th>
              </tr>
            </thead>
            <tbody>
              {events.map(event => (
                <tr key={event.id}>
                  <td>{event.id}</td>
                  <td>{event.eventType}</td>
                  <td>{event.actorId}</td>
                  <td>{event.resourceType}/{event.resourceId}</td>
                  <td>{new Date(event.timestamp).toLocaleString()}</td>
                  <td className="hash-cell" title={event.contentHash}>
                    {event.contentHash?.substring(0, 12)}...
                  </td>
                  <td className="hash-cell" title={event.chainHash}>
                    {event.chainHash?.substring(0, 12)}...
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default EventList;
