import { Alert, Box, Divider, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { aiopsApi } from 'api/aiopsApi';
import { analyzeIncident, IncidentAnalysis, IncidentAnalysisContext } from 'api/aiServiceApi';
import PageHeader from 'components/common/PageHeader';
import AiFallbackNotice from 'components/aiops/AiFallbackNotice';
import ApiState from 'components/aiops/ApiState';
import IconActionButton from 'components/aiops/IconActionButton';
import SeverityChip from 'components/aiops/SeverityChip';
import { formatEventType } from 'helpers/aiops';
import { formatAnalysisDuration, isCacheHit } from 'helpers/aiFallback';
import { useAiopsData } from 'hooks/useAiopsData';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { EventLog } from 'types/aiops';

const AiAnalysisPage = () => {
  const [searchParams] = useSearchParams();
  const eventIdParam = searchParams.get('eventId');
  const incidentIdParam = searchParams.get('incidentId');
  const incidentKeyParam = searchParams.get('incidentKey');
  const requestedEventId = eventIdParam ? Number(eventIdParam) : null;
  const requestedIncidentId = incidentIdParam ? Number(incidentIdParam) : null;
  const { alerts, loading, error, refresh } = useAiopsData();
  const [selectedEventId, setSelectedEventId] = useState<number | ''>('');
  const [requestedEvent, setRequestedEvent] = useState<EventLog | null>(null);
  const [incidentContext, setIncidentContext] = useState<IncidentAnalysisContext | null>(null);
  const [contextLoading, setContextLoading] = useState(false);
  const [analysis, setAnalysis] = useState<IncidentAnalysis | null>(null);
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  const [saveLoading, setSaveLoading] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [analysisGeneratedAt, setAnalysisGeneratedAt] = useState<string | null>(null);

  const availableIncidents = useMemo(() => {
    if (requestedEvent && !alerts.some((alert) => alert.id === requestedEvent.id)) {
      return [requestedEvent, ...alerts];
    }
    return alerts;
  }, [alerts, requestedEvent]);

  const selectedIncident = useMemo(
    () => availableIncidents.find((alert) => alert.id === selectedEventId) || null,
    [availableIncidents, selectedEventId],
  );

  useEffect(() => {
    setIncidentContext(null);
    if (requestedIncidentId) {
      const loadContext = async () => {
        try {
          setContextLoading(true);
          setIncidentContext(await aiopsApi.getIncidentAiContext(requestedIncidentId));
        } catch {
          setIncidentContext(null);
        } finally {
          setContextLoading(false);
        }
      };

      void loadContext();
      return;
    }

    setContextLoading(false);
    if (!incidentKeyParam) {
      return;
    }

    try {
      const stored = window.localStorage.getItem(incidentKeyParam);
      setIncidentContext(stored ? (JSON.parse(stored) as IncidentAnalysisContext) : null);
    } catch {
      setIncidentContext(null);
    }
  }, [incidentKeyParam, requestedIncidentId]);

  useEffect(() => {
    if (!requestedEventId) {
      setRequestedEvent(null);
      return;
    }

    const loadRequestedEvent = async () => {
      try {
        const events = await aiopsApi.getEvents();
        setRequestedEvent(events.find((event) => event.id === requestedEventId) || null);
      } catch {
        setRequestedEvent(null);
      }
    };

    loadRequestedEvent();
  }, [requestedEventId]);

  useEffect(() => {
    if (requestedEventId && availableIncidents.some((alert) => alert.id === requestedEventId)) {
      setSelectedEventId(requestedEventId);
      return;
    }

    if (!selectedEventId && availableIncidents.length) {
      const firstCritical =
        availableIncidents.find((alert) => alert.severity === 'CRITICAL') || availableIncidents[0];
      setSelectedEventId(firstCritical.id);
    }
  }, [availableIncidents, requestedEventId, selectedEventId]);

  const handleAnalyze = async () => {
    if (!selectedIncident) {
      return;
    }

    try {
      setAnalysisLoading(true);
      setAnalysisError(null);
      const response = await analyzeIncident(
        selectedIncident,
        incidentContext ? incidentContext : null,
      );
      setAnalysis(response);
      setAnalysisGeneratedAt(new Date().toISOString());
      setSaveMessage(null);
      setSaveError(null);
      await aiopsApi.logAuditAction('AI_ANALYSIS_REQUESTED', {
        targetType: 'EVENT',
        targetId: String(selectedIncident.id),
        details:
          `Requested AI analysis for ${selectedIncident.deviceName} ${selectedIncident.eventType}` +
          ` provider=${response.provider}` +
          ` requestedProvider=${response.requestedProvider ?? 'ollama'}` +
          ` model=${response.model ?? ''}` +
          ` fallbackUsed=${response.fallbackUsed === true}` +
          ` fallbackReasonCode=${response.fallbackReasonCode ?? 'none'}` +
          ` cacheHit=${response.cacheHit === true}` +
          ` analysisDurationMs=${response.analysisDurationMs ?? 'none'}` +
          ` contextFingerprint=${(response.contextFingerprint || 'none').slice(0, 12)}`,
      });
    } catch (requestError) {
      setAnalysisError(
        requestError instanceof Error ? requestError.message : 'Unknown AI service error',
      );
    } finally {
      setAnalysisLoading(false);
    }
  };

  const handleExportPdf = () => {
    if (selectedIncident) {
      void aiopsApi
        .logAuditAction('REPORT_EXPORTED', {
          targetType: 'EVENT',
          targetId: String(selectedIncident.id),
          details: `Exported AI analysis report for ${selectedIncident.deviceName}`,
        })
        .catch((auditError) => {
          console.warn('Audit logging failed for AI analysis export', auditError);
        });
    }
    window.print();
  };

  const handleSaveReport = async () => {
    if (!selectedIncident || !analysis) {
      return;
    }

    try {
      setSaveLoading(true);
      setSaveMessage(null);
      setSaveError(null);
      const savedReport = await aiopsApi.saveReport({
        incident: selectedIncident,
        analysis,
        context: incidentContext,
      });
      setSaveMessage(`Diagnostic report #${savedReport.id} saved.`);
    } catch (requestError) {
      setSaveError(requestError instanceof Error ? requestError.message : 'Unknown save error');
    } finally {
      setSaveLoading(false);
    }
  };

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={<IconActionButton icon="mdi:refresh" label="Refresh alerts" onClick={refresh} />}
      >
        AI Analysis
      </PageHeader>
      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />

        <Paper
          className="ai-analysis-card"
          sx={{ p: 3, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={1}>
            Incident Analysis Engine
          </Typography>
          <Typography color="text.secondary" mb={3}>
            Select an alert or incident and send it to the FastAPI AI service. Incident analysis
            includes related events, component configuration, and recent metrics when available.
          </Typography>

          <Stack spacing={2.5}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems="flex-end">
              <TextField
                className="compact-control"
                select
                fullWidth
                label={incidentContext ? 'Incident latest event' : 'Alert to analyze'}
                value={selectedEventId}
                onChange={(event) => {
                  setSelectedEventId(Number(event.target.value));
                  setAnalysis(null);
                  setAnalysisError(null);
                }}
              >
                {availableIncidents.map((alert) => (
                  <MenuItem key={alert.id} value={alert.id}>
                    {alert.deviceName} - {formatEventType(alert.eventType)} - {alert.severity}
                  </MenuItem>
                ))}
              </TextField>
              <IconActionButton
                icon={analysisLoading ? 'mdi:loading' : 'mdi:brain'}
                label={analysisLoading ? 'Analyzing incident' : 'Analyze incident'}
                onClick={handleAnalyze}
                disabled={
                  !selectedIncident ||
                  analysisLoading ||
                  (requestedIncidentId !== null && (contextLoading || !incidentContext))
                }
                color="primary"
                size="medium"
              />
            </Stack>

            {!alerts.length && !loading && (
              <Alert severity="info">
                No alert is available yet. Run simulators to generate data.
              </Alert>
            )}

            {selectedIncident && (
              <Paper
                variant="outlined"
                sx={{ p: 2, borderRadius: 2, bgcolor: 'background.default' }}
              >
                <Stack spacing={1}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <SeverityChip severity={selectedIncident.severity} />
                    <Typography fontWeight={700}>{selectedIncident.deviceName}</Typography>
                  </Stack>
                  <Typography>
                    {formatEventType(selectedIncident.eventType)} - {selectedIncident.message}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {selectedIncident.details || 'No additional details'}
                  </Typography>
                  {incidentContext && (
                    <Stack spacing={0.75} pt={1}>
                      <Typography variant="body2">
                        <strong>Incident:</strong> {incidentContext.title}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {incidentContext.status} · {incidentContext.eventCount} related events ·{' '}
                        {incidentContext.durationMinutes} minutes
                      </Typography>
                      {!!incidentContext.packetCaptureSummaries.length && (
                        <Typography variant="body2" color="text.secondary">
                          {incidentContext.packetCaptureSummaries.length} packet capture
                          {incidentContext.packetCaptureSummaries.length > 1 ? 's' : ''} available
                        </Typography>
                      )}
                      {!!incidentContext.networkFlowSummaries.length && (
                        <Typography variant="body2" color="text.secondary">
                          {incidentContext.networkFlowSummaries.length} NetFlow evidence group
                          {incidentContext.networkFlowSummaries.length > 1 ? 's' : ''} available
                        </Typography>
                      )}
                    </Stack>
                  )}
                </Stack>
              </Paper>
            )}

            {analysisError && <Alert severity="error">{analysisError}</Alert>}

            {analysis && (
              <Stack spacing={2.5}>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  spacing={1}
                  justifyContent="space-between"
                  alignItems={{ xs: 'flex-start', sm: 'center' }}
                  flexWrap="wrap"
                >
                  <Typography variant="body2" color="text.secondary">
                    Provider: {analysis.provider === 'ollama' ? 'Ollama' : analysis.provider}
                    {analysis.fallbackUsed ? ' (fallback)' : ''} · Priority: {analysis.priority}
                    {analysis.fallbackUsed === false || analysis.fallbackUsed === undefined
                      ? ' · Fallback used: No'
                      : ' · Fallback used: Yes'}
                    {` · Cached: ${isCacheHit(analysis) ? 'Yes' : 'No'}`}
                    {formatAnalysisDuration(analysis.analysisDurationMs) &&
                      ` · Analysis time: ${formatAnalysisDuration(analysis.analysisDurationMs)}`}
                  </Typography>
                  <Stack direction="row" spacing={1}>
                    {analysis.model && (
                      <Typography variant="body2" color="text.secondary">
                        Model: {analysis.model}
                      </Typography>
                    )}
                    <IconActionButton
                      icon="mdi:file-pdf-box"
                      label="Export PDF"
                      onClick={handleExportPdf}
                    />
                    <IconActionButton
                      icon={saveLoading ? 'mdi:loading' : 'mdi:content-save-outline'}
                      label={saveLoading ? 'Saving report' : 'Save report'}
                      onClick={handleSaveReport}
                      disabled={saveLoading}
                      color="primary"
                    />
                  </Stack>
                </Stack>

                {saveMessage && <Alert severity="success">{saveMessage}</Alert>}
                {saveError && <Alert severity="error">{saveError}</Alert>}
                <AiFallbackNotice analysis={analysis} />

                {selectedIncident && (
                  <Paper
                    className="diagnostic-report"
                    variant="outlined"
                    sx={{
                      p: 3,
                      borderRadius: 2,
                      bgcolor: 'background.paper',
                    }}
                  >
                    <Stack spacing={2.5}>
                      <Stack spacing={0.5}>
                        <Typography variant="h4">AIOps Diagnostic Report</Typography>
                        <Typography color="text.secondary">
                          Generated on{' '}
                          {dayjs(analysisGeneratedAt ?? undefined).format('DD/MM/YYYY HH:mm:ss')}
                        </Typography>
                      </Stack>

                      <Divider />

                      <Stack spacing={1}>
                        <Typography variant="h6">Incident</Typography>
                        {incidentContext && (
                          <>
                            <Typography>
                              <strong>Incident title:</strong> {incidentContext.title}
                            </Typography>
                            <Typography>
                              <strong>Incident status:</strong> {incidentContext.status}
                            </Typography>
                            <Typography>
                              <strong>Related events:</strong> {incidentContext.eventCount}
                            </Typography>
                            <Typography>
                              <strong>Duration:</strong> {incidentContext.durationMinutes} minutes
                            </Typography>
                          </>
                        )}
                        <Typography>
                          <strong>Device:</strong> {selectedIncident.deviceName}
                        </Typography>
                        <Typography>
                          <strong>Component type:</strong> {selectedIncident.deviceType}
                        </Typography>
                        <Typography>
                          <strong>Location:</strong> {selectedIncident.location}
                        </Typography>
                        <Typography>
                          <strong>Severity:</strong> {selectedIncident.severity}
                        </Typography>
                        <Typography>
                          <strong>Event:</strong> {formatEventType(selectedIncident.eventType)}
                        </Typography>
                        <Typography>
                          <strong>Occurred at:</strong>{' '}
                          {dayjs(selectedIncident.occurredAt).format('DD/MM/YYYY HH:mm:ss')}
                        </Typography>
                        <Typography>
                          <strong>Message:</strong> {selectedIncident.message}
                        </Typography>
                        <Typography>
                          <strong>Details:</strong>{' '}
                          {selectedIncident.details || 'No additional details'}
                        </Typography>
                      </Stack>

                      {incidentContext && (
                        <>
                          <Divider />

                          <Stack spacing={1}>
                            <Typography variant="h6">Incident Context</Typography>
                            {incidentContext.component && (
                              <Typography>
                                <strong>Component methods:</strong>{' '}
                                {incidentContext.component.monitoringMethods.join(', ')}
                              </Typography>
                            )}
                            <Typography>
                              <strong>Recent related events:</strong>
                            </Typography>
                            <Stack component="ol" sx={{ pl: 3, my: 0 }}>
                              {incidentContext.relatedEvents.slice(0, 6).map((event) => (
                                <Typography component="li" key={event.id}>
                                  {formatEventType(event.eventType)} - {event.message}
                                </Typography>
                              ))}
                            </Stack>
                            {!!incidentContext.previousSimilarIncidents.length && (
                              <>
                                <Typography>
                                  <strong>Previous similar incidents:</strong>
                                </Typography>
                                <Stack component="ul" sx={{ pl: 3, my: 0 }}>
                                  {incidentContext.previousSimilarIncidents
                                    .slice(0, 3)
                                    .map((incident) => (
                                      <Typography component="li" key={incident.id}>
                                        {incident.title} - {incident.severity} -{' '}
                                        {incident.eventCount} events
                                      </Typography>
                                    ))}
                                </Stack>
                              </>
                            )}
                            {!!incidentContext.packetCaptureSummaries.length && (
                              <>
                                <Typography>
                                  <strong>Packet capture evidence:</strong>
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {incidentContext.relatedEvents.some(
                                    (event) => event.eventSource === 'SYSLOG',
                                  )
                                    ? 'Syslog evidence and packet capture evidence are both available for this incident.'
                                    : 'Packet capture evidence is available for this incident.'}
                                </Typography>
                                <Stack spacing={1}>
                                  {incidentContext.packetCaptureSummaries.map((capture) => (
                                    <Paper
                                      key={capture.id}
                                      variant="outlined"
                                      sx={{
                                        p: 1.5,
                                        borderRadius: 2,
                                        bgcolor: 'background.default',
                                      }}
                                    >
                                      <Stack spacing={0.5}>
                                        <Typography fontWeight={700}>
                                          {capture.fileName} ·{' '}
                                          {dayjs(capture.createdAt).format('DD/MM/YYYY HH:mm:ss')}
                                        </Typography>
                                        <Typography variant="body2">
                                          {`${capture.totalPackets} packets · ${capture.totalBytes} bytes`}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Top source IPs:</strong>{' '}
                                          {capture.topSourceIps.join(', ') || '-'}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Top destination IPs:</strong>{' '}
                                          {capture.topDestinationIps.join(', ') || '-'}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Top protocols:</strong>{' '}
                                          {capture.topProtocols.join(', ') || '-'}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Top destination ports:</strong>{' '}
                                          {capture.topDestinationPorts.join(', ') || '-'}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Suspicious findings:</strong>{' '}
                                          {capture.suspiciousFindings.join(' | ') || '-'}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Summary:</strong> {capture.summary}
                                        </Typography>
                                      </Stack>
                                    </Paper>
                                  ))}
                                </Stack>
                              </>
                            )}
                            {!!incidentContext.networkFlowSummaries.length && (
                              <>
                                <Typography>
                                  <strong>NetFlow evidence:</strong>
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {incidentContext.relatedEvents.some(
                                    (event) => event.eventSource === 'SYSLOG',
                                  ) && incidentContext.packetCaptureSummaries.length
                                    ? 'Syslog, NetFlow, and packet capture evidence support this incident.'
                                    : incidentContext.relatedEvents.some(
                                          (event) => event.eventSource === 'SYSLOG',
                                        )
                                      ? 'Syslog evidence and NetFlow evidence are both available for this incident.'
                                      : 'NetFlow evidence is available for this incident.'}
                                </Typography>
                                <Stack spacing={1}>
                                  {incidentContext.networkFlowSummaries.map((flow) => (
                                    <Paper
                                      key={`${flow.sourceIp}-${flow.destinationIp}-${flow.anomalyType}-${flow.firstSeenAt}`}
                                      variant="outlined"
                                      sx={{
                                        p: 1.5,
                                        borderRadius: 2,
                                        bgcolor: 'background.default',
                                      }}
                                    >
                                      <Stack spacing={0.5}>
                                        <Typography fontWeight={700}>
                                          {flow.anomalyType || 'FLOW'} · {flow.sourceIp} to{' '}
                                          {flow.destinationIp}
                                        </Typography>
                                        <Typography variant="body2">
                                          {flow.flowCount} flows · {flow.totalPackets} packets ·{' '}
                                          {flow.totalBytes} bytes
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Destination ports:</strong>{' '}
                                          {flow.destinationPorts.join(', ') || '-'}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Protocols:</strong>{' '}
                                          {flow.protocols.join(', ') || '-'}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Source:</strong> {flow.sourceName || '-'}
                                        </Typography>
                                        <Typography variant="body2">
                                          <strong>Reason:</strong> {flow.anomalyReason || '-'}
                                        </Typography>
                                      </Stack>
                                    </Paper>
                                  ))}
                                </Stack>
                              </>
                            )}
                          </Stack>
                        </>
                      )}

                      <Divider />

                      <Stack spacing={1}>
                        <Typography variant="h6">AI Analysis</Typography>
                        <Typography>
                          <strong>Provider:</strong>{' '}
                          {analysis.provider === 'ollama' ? 'Ollama' : analysis.provider}
                          {analysis.model ? ` (${analysis.model})` : ''}
                        </Typography>
                        <Typography>
                          <strong>Cached:</strong> {isCacheHit(analysis) ? 'Yes' : 'No'}
                          {formatAnalysisDuration(analysis.analysisDurationMs)
                            ? ` · Analysis time: ${formatAnalysisDuration(analysis.analysisDurationMs)}`
                            : ''}
                        </Typography>
                        <Typography>
                          <strong>Priority:</strong> {analysis.priority}
                        </Typography>
                        <Typography>
                          <strong>Summary:</strong> {analysis.summary}
                        </Typography>
                        <Typography>
                          <strong>Impact:</strong> {analysis.impact}
                        </Typography>
                        <Typography>
                          <strong>Risk:</strong> {analysis.risk}
                        </Typography>
                      </Stack>

                      <Stack spacing={1}>
                        <Typography variant="h6">Probable Causes</Typography>
                        <Stack component="ol" sx={{ pl: 3, my: 0 }}>
                          {analysis.probable_causes.map((cause) => (
                            <Typography component="li" key={cause}>
                              {cause}
                            </Typography>
                          ))}
                        </Stack>
                      </Stack>

                      <Stack spacing={1}>
                        <Typography variant="h6">Suggested Actions</Typography>
                        <Stack component="ol" sx={{ pl: 3, my: 0 }}>
                          {analysis.suggested_actions.map((action) => (
                            <Typography component="li" key={action}>
                              {action}
                            </Typography>
                          ))}
                        </Stack>
                      </Stack>

                      <Stack spacing={1}>
                        <Typography variant="h6">Diagnostic Commands</Typography>
                        <Stack spacing={1}>
                          {analysis.diagnostic_commands.map((command) => (
                            <Typography
                              key={command}
                              fontFamily="monospace"
                              sx={{
                                px: 1.5,
                                py: 1,
                                borderRadius: 1,
                                bgcolor: 'grey.100',
                                overflowWrap: 'anywhere',
                              }}
                            >
                              {command}
                            </Typography>
                          ))}
                        </Stack>
                      </Stack>
                    </Stack>
                  </Paper>
                )}
              </Stack>
            )}
          </Stack>
        </Paper>
      </Stack>
    </Box>
  );
};

export default AiAnalysisPage;
