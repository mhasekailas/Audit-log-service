import React, { useState } from 'react';

function ChainVerification({ onVerify }) {
  const [verificationResult, setVerificationResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const verifyChain = async () => {
    setLoading(true);
    setError(null);
    setVerificationResult(null);

    try {
      const response = await fetch('/audit/verify');
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      setVerificationResult(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="verification-container">
      <h2>Chain Verification</h2>
      
      <div className="verification-controls">
        <p className="help-text">
          Verify the integrity of the entire audit log hash chain. This will check that no records have been tampered with.
        </p>
        <button 
          className="btn btn-primary" 
          onClick={verifyChain}
          disabled={loading}
        >
          {loading ? 'Verifying...' : 'Verify Chain Integrity'}
        </button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {verificationResult && (
        <div className="verification-result">
          <div className={`status ${verificationResult.isValid ? 'valid' : 'invalid'}`}>
            {verificationResult.isValid ? '✓ Chain is Valid' : '✗ Chain Integrity Broken'}
          </div>

          <div className="result-details">
            <div className="detail-row">
              <span>Total Records:</span>
              <strong>{verificationResult.totalRecords}</strong>
            </div>

            {!verificationResult.isValid && verificationResult.firstBreach && (
              <div className="breach-info">
                <h3>⚠️ First Breach Detected</h3>
                <div className="detail-row">
                  <span>Record ID:</span>
                  <strong>{verificationResult.firstBreach.recordId}</strong>
                </div>
                <div className="detail-row">
                  <span>Expected Hash:</span>
                  <code className="hash-display">{verificationResult.firstBreach.expectedHash}</code>
                </div>
                <div className="detail-row">
                  <span>Actual Hash:</span>
                  <code className="hash-display">{verificationResult.firstBreach.actualHash}</code>
                </div>
                <div className="detail-row">
                  <span>Violation Type:</span>
                  <strong className="violation-type">{verificationResult.firstBreach.violationType}</strong>
                </div>
              </div>
            )}

            {verificationResult.isValid && (
              <div className="success-message">
                <p>✓ All {verificationResult.totalRecords} records have been verified and no tampering was detected.</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default ChainVerification;
