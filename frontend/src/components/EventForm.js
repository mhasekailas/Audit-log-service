import React, { useState } from 'react';
import { authorizedFetch } from '../api';

function EventForm({ onEventCreated }) {
  const [formData, setFormData] = useState({
    eventType: 'USER_LOGIN',
    actorId: '',
    resourceType: 'ACCOUNT',
    resourceId: '',
    payload: '{}'
  });
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccess(false);

    try {
      // Validate JSON payload
      JSON.parse(formData.payload);

      const response = await authorizedFetch('/audit/events', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          ...formData,
          payload: JSON.parse(formData.payload)
        })
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      setSuccess(true);
      setFormData({
        eventType: 'USER_LOGIN',
        actorId: '',
        resourceType: 'ACCOUNT',
        resourceId: '',
        payload: '{}'
      });

      setTimeout(() => onEventCreated(), 1500);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="form-container">
      <h2>Create New Event</h2>
      
      {error && <div className="alert alert-error">{error}</div>}
      {success && <div className="alert alert-success">Event created successfully!</div>}

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Event Type</label>
          <select name="eventType" value={formData.eventType} onChange={handleChange}>
            <option value="USER_LOGIN">USER_LOGIN</option>
            <option value="USER_LOGOUT">USER_LOGOUT</option>
            <option value="RECORD_UPDATED">RECORD_UPDATED</option>
            <option value="PERMISSION_GRANTED">PERMISSION_GRANTED</option>
            <option value="PERMISSION_REVOKED">PERMISSION_REVOKED</option>
            <option value="DATA_ACCESSED">DATA_ACCESSED</option>
            <option value="DATA_EXPORTED">DATA_EXPORTED</option>
          </select>
        </div>

        <div className="form-group">
          <label>Actor ID</label>
          <input 
            type="text" 
            name="actorId" 
            value={formData.actorId} 
            onChange={handleChange}
            placeholder="e.g., user123"
            required
          />
        </div>

        <div className="form-group">
          <label>Resource Type</label>
          <input 
            type="text" 
            name="resourceType" 
            value={formData.resourceType} 
            onChange={handleChange}
            placeholder="e.g., ACCOUNT"
            required
          />
        </div>

        <div className="form-group">
          <label>Resource ID</label>
          <input 
            type="text" 
            name="resourceId" 
            value={formData.resourceId} 
            onChange={handleChange}
            placeholder="e.g., acc-456"
            required
          />
        </div>

        <div className="form-group">
          <label>JSON Payload</label>
          <textarea 
            name="payload" 
            value={formData.payload} 
            onChange={handleChange}
            rows="6"
            placeholder='{"key": "value"}'
            required
          />
        </div>

        <button type="submit" disabled={loading} className="btn btn-primary">
          {loading ? 'Creating...' : 'Create Event'}
        </button>
      </form>
    </div>
  );
}

export default EventForm;
