import React, { useState } from 'react';
import { authorizedFetch } from '../api';

function ScenarioC() {
  const [access, setAccess] = useState({ auditEventId: '', accessType: 'READ', userRole: '', ipAddress: '', userAgent: '', accessResult: 'SUCCESS' });
  const [filters, setFilters] = useState({ actorId: '', resourceId: '', accessType: '' });
  const [result, setResult] = useState(null);
  const [message, setMessage] = useState(null);

  const call = async (url, options = {}) => {
    const response = await authorizedFetch(url, options);
    const body = await response.json();
    if (!response.ok) throw new Error(body.error || `HTTP error! status: ${response.status}`);
    return body;
  };

  const recordAccess = async (event) => {
    event.preventDefault();
    try {
      const body = await call('/audit/compliance/access', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...access, auditEventId: Number(access.auditEventId) }) });
      setResult(body.data);
      setMessage('Access decision recorded.');
    } catch (error) { setMessage(error.message); }
  };

  const generateReport = async (event) => {
    event.preventDefault();
    try {
      const params = new URLSearchParams();
      Object.entries(filters).forEach(([key, value]) => value && params.set(key, value));
      setResult(await call(`/audit/compliance-report?${params.toString()}`));
      setMessage('Compliance report generated.');
    } catch (error) { setMessage(error.message); }
  };

  return (
    <div className="scenario-b-container">
      <h2>Compliance Reporting</h2>
      <p className="help-text">Record account-data access decisions and generate regulator-ready JSON reports.</p>
      {message && <div className={`alert ${message.includes('generated') || message.includes('recorded') ? 'alert-success' : 'alert-error'}`}>{message}</div>}
      <section className="form-container">
        <h3>Record Access Decision</h3>
        <form onSubmit={recordAccess}>
          <div className="form-group"><label>Audit Event ID</label><input value={access.auditEventId} onChange={(e) => setAccess({ ...access, auditEventId: e.target.value })} required /></div>
          <div className="form-group"><label>Access Type</label><input value={access.accessType} onChange={(e) => setAccess({ ...access, accessType: e.target.value })} required /></div>
          <div className="form-group"><label>User Role</label><input value={access.userRole} onChange={(e) => setAccess({ ...access, userRole: e.target.value })} /></div>
          <div className="form-group"><label>IP Address</label><input value={access.ipAddress} onChange={(e) => setAccess({ ...access, ipAddress: e.target.value })} /></div>
          <div className="form-group"><label>User Agent</label><input value={access.userAgent} onChange={(e) => setAccess({ ...access, userAgent: e.target.value })} /></div>
          <div className="form-group"><label>Access Result</label><select value={access.accessResult} onChange={(e) => setAccess({ ...access, accessResult: e.target.value })}><option>SUCCESS</option><option>DENIED</option></select></div>
          <button className="btn btn-primary">Record Access</button>
        </form>
      </section>
      <section className="form-container">
        <h3>Generate Report</h3>
        <form onSubmit={generateReport}>
          <div className="form-group"><label>Actor ID</label><input value={filters.actorId} onChange={(e) => setFilters({ ...filters, actorId: e.target.value })} /></div>
          <div className="form-group"><label>Resource ID</label><input value={filters.resourceId} onChange={(e) => setFilters({ ...filters, resourceId: e.target.value })} /></div>
          <div className="form-group"><label>Access Type</label><input value={filters.accessType} onChange={(e) => setFilters({ ...filters, accessType: e.target.value })} placeholder="READ or EXPORT" /></div>
          <button className="btn btn-primary">Generate Report</button>
        </form>
      </section>
      {result && <pre className="result-panel">{JSON.stringify(result, null, 2)}</pre>}
    </div>
  );
}

export default ScenarioC;
