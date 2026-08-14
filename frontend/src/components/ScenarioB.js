import React, { useState } from 'react';
import { authorizedFetch } from '../api';

function ScenarioB() {
  const [retention, setRetention] = useState({ resourceType: 'ACCOUNT', retentionDays: 30 });
  const [redaction, setRedaction] = useState({ eventId: '', fieldPaths: 'accountNumber', reason: 'Privacy request' });
  const [exportQuery, setExportQuery] = useState({ actorId: '', resourceId: '' });
  const [message, setMessage] = useState(null);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);

  const request = async (url, options = {}) => {
    const response = await authorizedFetch(url, options);
    const body = await response.json();
    if (!response.ok) throw new Error(body.error || `HTTP error! status: ${response.status}`);
    return body;
  };

  const run = async (operation) => {
    setLoading(true);
    setMessage(null);
    setResult(null);
    try {
      const data = await operation();
      setResult(data);
      setMessage('Operation completed successfully.');
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoading(false);
    }
  };

  const saveRetention = (event) => {
    event.preventDefault();
    run(() => request('/audit/retention-policies', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...retention, retentionDays: Number(retention.retentionDays), archiveOnExpiry: true })
    }));
  };

  const redactEvent = (event) => {
    event.preventDefault();
    run(() => request(`/audit/events/${redaction.eventId}/redact`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fieldPaths: redaction.fieldPaths.split(',').map((path) => path.trim()).filter(Boolean),
        reason: redaction.reason
      })
    }));
  };

  const archiveExpired = () => run(() => request('/audit/retention/archive', { method: 'POST' }));

  const exportRecords = (event) => {
    event.preventDefault();
    const params = new URLSearchParams();
    if (exportQuery.actorId) params.set('actorId', exportQuery.actorId);
    if (exportQuery.resourceId) params.set('resourceId', exportQuery.resourceId);
    run(() => request(`/audit/export?${params.toString()}`));
  };

  return (
    <div className="scenario-b-container">
      <h2>Retention, Redaction & Export</h2>
      <p className="help-text">Manage retention policies, redact sensitive payload fields, and export verifiable record bundles.</p>
      {message && <div className={`alert ${message.includes('successfully') ? 'alert-success' : 'alert-error'}`}>{message}</div>}

      <section className="form-container">
        <h3>Retention Policy</h3>
        <form onSubmit={saveRetention}>
          <div className="form-group"><label>Resource Type</label><input value={retention.resourceType} onChange={(e) => setRetention({ ...retention, resourceType: e.target.value })} required /></div>
          <div className="form-group"><label>Retention Days</label><input type="number" min="1" value={retention.retentionDays} onChange={(e) => setRetention({ ...retention, retentionDays: e.target.value })} required /></div>
          <button className="btn btn-primary" disabled={loading}>Save Policy</button>
          <button type="button" className="btn btn-secondary" onClick={archiveExpired} disabled={loading}>Archive Expired</button>
        </form>
      </section>

      <section className="form-container">
        <h3>Structured Redaction</h3>
        <form onSubmit={redactEvent}>
          <div className="form-group"><label>Event ID</label><input value={redaction.eventId} onChange={(e) => setRedaction({ ...redaction, eventId: e.target.value })} placeholder="e.g., 1" required /></div>
          <div className="form-group"><label>Payload Field Paths</label><input value={redaction.fieldPaths} onChange={(e) => setRedaction({ ...redaction, fieldPaths: e.target.value })} placeholder="accountNumber, customer.identifier" required /></div>
          <div className="form-group"><label>Reason</label><input value={redaction.reason} onChange={(e) => setRedaction({ ...redaction, reason: e.target.value })} required /></div>
          <button className="btn btn-primary" disabled={loading}>Redact Fields</button>
        </form>
      </section>

      <section className="form-container">
        <h3>Verifiable Bulk Export</h3>
        <form onSubmit={exportRecords}>
          <div className="form-group"><label>Actor ID</label><input value={exportQuery.actorId} onChange={(e) => setExportQuery({ ...exportQuery, actorId: e.target.value, resourceId: '' })} placeholder="Use actor ID or resource ID" /></div>
          <div className="form-group"><label>Resource ID</label><input value={exportQuery.resourceId} onChange={(e) => setExportQuery({ ...exportQuery, resourceId: e.target.value, actorId: '' })} placeholder="Use resource ID or actor ID" /></div>
          <button className="btn btn-primary" disabled={loading || (!exportQuery.actorId && !exportQuery.resourceId)}>Export Bundle</button>
        </form>
      </section>

      {result && <pre className="result-panel">{JSON.stringify(result, null, 2)}</pre>}
    </div>
  );
}

export default ScenarioB;
