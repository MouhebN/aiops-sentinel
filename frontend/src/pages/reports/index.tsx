import { Alert, Box, Paper, Stack, Typography } from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { aiopsApi, DiagnosticReport } from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import { canSaveReports } from 'auth/permissions';
import PageHeader from 'components/common/PageHeader';
import IconActionButton from 'components/aiops/IconActionButton';
import SeverityChip from 'components/aiops/SeverityChip';
import AiFallbackNotice from 'components/aiops/AiFallbackNotice';
import { formatDeviceType, formatEventType } from 'helpers/aiops';
import { formatAnalysisDuration, isCacheHit } from 'helpers/aiFallback';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';

const columns: GridColDef<DiagnosticReport>[] = [
  {
    field: 'generatedAt',
    headerName: 'Generated',
    minWidth: 170,
    flex: 1,
    valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm:ss'),
  },
  { field: 'deviceName', headerName: 'Device', minWidth: 180, flex: 1 },
  {
    field: 'deviceType',
    headerName: 'Type',
    minWidth: 130,
    flex: 0.7,
    valueGetter: (value) => formatDeviceType(value as string),
  },
  {
    field: 'eventType',
    headerName: 'Incident',
    minWidth: 190,
    flex: 1,
    valueGetter: (value) => formatEventType(value as string),
  },
  {
    field: 'severity',
    headerName: 'Severity',
    minWidth: 130,
    flex: 0.7,
    renderCell: (params) => <SeverityChip severity={params.row.severity} />,
  },
  { field: 'priority', headerName: 'Priority', minWidth: 110, flex: 0.5 },
  {
    field: 'provider',
    headerName: 'AI Provider',
    minWidth: 160,
    flex: 0.8,
    valueGetter: (_value, row) => {
      const provider = row.provider === 'ollama' ? 'Ollama' : row.provider;
      return row.fallbackUsed ? `${provider} (fallback)` : provider;
    },
  },
  { field: 'summary', headerName: 'Summary', minWidth: 300, flex: 1.8 },
];

const ReportsPage = () => {
  const { user } = useAuth();
  const canExport = canSaveReports(user?.role);
  const [reports, setReports] = useState<DiagnosticReport[]>([]);
  const [selectedReport, setSelectedReport] = useState<DiagnosticReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await aiopsApi.getReports();
      setReports(response);
      setSelectedReport((currentReport) =>
        currentReport ? response.find((report) => report.id === currentReport.id) || null : null,
      );
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown reports error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const handleExportPdf = async (report: DiagnosticReport) => {
    try {
      await aiopsApi.logAuditAction('REPORT_EXPORTED', {
        targetType: 'REPORT',
        targetId: String(report.id),
        details: `Exported diagnostic report for ${report.deviceName}`,
      });
    } catch (auditError) {
      console.warn('Audit logging failed for diagnostic report export', auditError);
    }
    window.print();
  };

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={<IconActionButton icon="mdi:refresh" label="Refresh reports" onClick={refresh} />}
      >
        Diagnostic Reports
      </PageHeader>

      <Stack spacing={3} mt={3}>
        {error && <Alert severity="error">{error}</Alert>}

        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Saved AI Diagnostic Reports
          </Typography>
          <DataGrid
            rows={reports}
            columns={columns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            pageSizeOptions={[5, 10, 25]}
            initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
            onRowClick={(params) => setSelectedReport(params.row)}
          />
        </Paper>

        {selectedReport && (
          <Paper
            className="diagnostic-report"
            sx={{ p: 3, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
            elevation={0}
          >
            <Stack spacing={2}>
              <Stack
                direction={{ xs: 'column', md: 'row' }}
                justifyContent="space-between"
                spacing={1}
              >
                <Stack>
                  <Typography variant="h4">Diagnostic Report #{selectedReport.id}</Typography>
                  <Typography color="text.secondary">
                    Generated {dayjs(selectedReport.generatedAt).format('DD/MM/YYYY HH:mm:ss')}
                  </Typography>
                </Stack>
                {canExport && (
                  <IconActionButton
                    icon="mdi:file-pdf-box"
                    label="Export PDF"
                    onClick={() => handleExportPdf(selectedReport)}
                  />
                )}
              </Stack>

              <AiFallbackNotice analysis={selectedReport} />

              <Stack spacing={0.5}>
                <Typography>
                  <strong>Device:</strong> {selectedReport.deviceName}
                </Typography>
                <Typography>
                  <strong>Type:</strong> {formatDeviceType(selectedReport.deviceType)}
                </Typography>
                <Typography>
                  <strong>Location:</strong> {selectedReport.location}
                </Typography>
                <Typography>
                  <strong>Incident:</strong> {formatEventType(selectedReport.eventType)}
                </Typography>
                <Typography>
                  <strong>Severity:</strong> {selectedReport.severity}
                </Typography>
                <Typography>
                  <strong>Priority:</strong> {selectedReport.priority}
                </Typography>
                <Typography>
                  <strong>Provider:</strong>{' '}
                  {selectedReport.provider === 'ollama' ? 'Ollama' : selectedReport.provider}
                  {selectedReport.fallbackUsed ? ' (fallback)' : ''}
                  {selectedReport.model ? ` · Model: ${selectedReport.model}` : ''}
                </Typography>
                <Typography>
                  <strong>Cached:</strong> {isCacheHit(selectedReport) ? 'Yes' : 'No'}
                  {formatAnalysisDuration(selectedReport.analysisDurationMs)
                    ? ` · Analysis time: ${formatAnalysisDuration(selectedReport.analysisDurationMs)}`
                    : ''}
                </Typography>
                <Typography>
                  <strong>Message:</strong> {selectedReport.message}
                </Typography>
              </Stack>

              <Stack>
                <Typography variant="h6">AI Summary</Typography>
                <Typography>{selectedReport.summary}</Typography>
              </Stack>

              <Stack>
                <Typography variant="h6">Impact</Typography>
                <Typography>{selectedReport.impact}</Typography>
              </Stack>

              <Stack>
                <Typography variant="h6">Risk</Typography>
                <Typography>{selectedReport.risk}</Typography>
              </Stack>

              <Stack>
                <Typography variant="h6">Probable Causes</Typography>
                <Stack component="ol" sx={{ pl: 3, my: 0 }}>
                  {selectedReport.probableCauses.map((cause) => (
                    <Typography component="li" key={cause}>
                      {cause}
                    </Typography>
                  ))}
                </Stack>
              </Stack>

              <Stack>
                <Typography variant="h6">Suggested Actions</Typography>
                <Stack component="ol" sx={{ pl: 3, my: 0 }}>
                  {selectedReport.suggestedActions.map((action) => (
                    <Typography component="li" key={action}>
                      {action}
                    </Typography>
                  ))}
                </Stack>
              </Stack>

              <Stack>
                <Typography variant="h6">Diagnostic Commands</Typography>
                <Stack spacing={1}>
                  {selectedReport.diagnosticCommands.map((command) => (
                    <Typography
                      key={command}
                      fontFamily="monospace"
                      sx={{ px: 1.5, py: 1, borderRadius: 1, bgcolor: 'grey.100' }}
                    >
                      {command}
                    </Typography>
                  ))}
                </Stack>
              </Stack>

              {!!selectedReport.packetCaptureSummaries.length && (
                <Stack spacing={1.25}>
                  <Typography variant="h6">Packet Capture Evidence</Typography>
                  {selectedReport.packetCaptureSummaries.map((capture) => (
                    <Paper
                      key={capture.id}
                      variant="outlined"
                      sx={{ p: 1.5, borderRadius: 2, bgcolor: 'background.default' }}
                    >
                      <Stack spacing={0.5}>
                        <Typography fontWeight={700}>
                          {capture.fileName} ·{' '}
                          {dayjs(capture.createdAt).format('DD/MM/YYYY HH:mm:ss')}
                        </Typography>
                        <Typography variant="body2">
                          {capture.totalPackets} packets · {capture.totalBytes} bytes
                        </Typography>
                        <Typography variant="body2">
                          <strong>Top source IPs:</strong> {capture.topSourceIps.join(', ') || '-'}
                        </Typography>
                        <Typography variant="body2">
                          <strong>Top destination IPs:</strong>{' '}
                          {capture.topDestinationIps.join(', ') || '-'}
                        </Typography>
                        <Typography variant="body2">
                          <strong>Top protocols:</strong> {capture.topProtocols.join(', ') || '-'}
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
              )}

              {!!selectedReport.networkFlowSummaries.length && (
                <Stack spacing={1.25}>
                  <Typography variant="h6">NetFlow Evidence</Typography>
                  {selectedReport.networkFlowSummaries.map((flow, index) => (
                    <Paper
                      key={`${flow.sourceIp}-${flow.destinationIp}-${flow.anomalyType}-${index}`}
                      variant="outlined"
                      sx={{ p: 1.5, borderRadius: 2, bgcolor: 'background.default' }}
                    >
                      <Stack spacing={0.5}>
                        <Typography fontWeight={700}>
                          {flow.anomalyType || 'FLOW'} · {flow.sourceIp} to {flow.destinationIp}
                        </Typography>
                        <Typography variant="body2">
                          {flow.flowCount} flows · {flow.totalPackets} packets · {flow.totalBytes}{' '}
                          bytes
                        </Typography>
                        <Typography variant="body2">
                          <strong>Destination ports:</strong>{' '}
                          {flow.destinationPorts.join(', ') || '-'}
                        </Typography>
                        <Typography variant="body2">
                          <strong>Protocols:</strong> {flow.protocols.join(', ') || '-'}
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
              )}
            </Stack>
          </Paper>
        )}
      </Stack>
    </Box>
  );
};

export default ReportsPage;
