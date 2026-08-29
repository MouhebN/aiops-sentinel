import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  Stack,
  Typography,
} from '@mui/material';
import {
  aiopsApi,
  IncidentStatus,
  PacketCaptureAnalysis,
  PacketCaptureJob,
  PacketCapturePreview,
  PacketCaptureProviderHealth,
} from 'api/aiopsApi';
import IconActionButton from 'components/aiops/IconActionButton';
import PcapSizeMetric, {
  formatByteCount,
  PCAP_FILE_SIZE_HINT,
} from 'components/aiops/PcapSizeMetric';
import { isIncidentResolved } from 'helpers/aiops';
import { useCallback, useEffect, useMemo, useState } from 'react';

const jobChipColor = (status: PacketCaptureJob['status']) => {
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'FAILED' || status === 'CANCELLED') {
    return 'error';
  }
  if (status === 'RUNNING' || status === 'PENDING') {
    return 'info';
  }
  return 'default';
};

interface PacketCapturePanelProps {
  incidentId: number;
  incidentStatus?: IncidentStatus | null;
  canCapture: boolean;
  packetCaptures: PacketCaptureAnalysis[];
  uploadingPcap?: boolean;
  onUploadClick?: () => void;
  onPacketCapturesChange: (captures: PacketCaptureAnalysis[]) => void;
  onError: (message: string | null) => void;
}

const triggerLabel = (job: PacketCaptureJob) => {
  if (job.trigger === 'AUTO_ROLLING') {
    return 'AUTO_ROLLING';
  }
  if (job.trigger === 'AUTO_INCIDENT') {
    return 'AUTO_INCIDENT';
  }
  return 'MANUAL';
};

const isAutomatic = (job: PacketCaptureJob | null) =>
  job?.trigger === 'AUTO_ROLLING' || job?.trigger === 'AUTO_INCIDENT';

const PacketCapturePanel = ({
  incidentId,
  incidentStatus,
  canCapture,
  packetCaptures,
  uploadingPcap,
  onUploadClick,
  onPacketCapturesChange,
  onError,
}: PacketCapturePanelProps) => {
  const [jobs, setJobs] = useState<PacketCaptureJob[]>([]);
  const [preview, setPreview] = useState<PacketCapturePreview | null>(null);
  const [health, setHealth] = useState<PacketCaptureProviderHealth | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [starting, setStarting] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const latestJob = jobs[0] ?? null;
  const running = latestJob?.status === 'PENDING' || latestJob?.status === 'RUNNING';
  const liveCaptureDisabled = isIncidentResolved(incidentStatus);

  const refreshJobsAndCaptures = useCallback(async () => {
    const [jobResponse, captureResponse] = await Promise.all([
      aiopsApi.getPacketCaptureJobs(incidentId),
      aiopsApi.getIncidentPacketCaptures(incidentId),
    ]);
    setJobs(jobResponse);
    onPacketCapturesChange(captureResponse);
  }, [incidentId, onPacketCapturesChange]);

  useEffect(() => {
    void refreshJobsAndCaptures().catch((requestError: unknown) => {
      onError(requestError instanceof Error ? requestError.message : 'Could not load capture jobs');
    });
  }, [incidentId, onError, refreshJobsAndCaptures]);

  useEffect(() => {
    if (!running || !latestJob) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      setNow(Date.now());
      void aiopsApi
        .getPacketCaptureJob(incidentId, latestJob.id)
        .then(async (job) => {
          setJobs((current) => [job, ...current.filter((item) => item.id !== job.id)]);
          if (job.status === 'COMPLETED' || job.status === 'FAILED' || job.status === 'CANCELLED') {
            const captures = await aiopsApi.getIncidentPacketCaptures(incidentId);
            onPacketCapturesChange(captures);
          }
        })
        .catch((requestError: unknown) => {
          onError(
            requestError instanceof Error
              ? requestError.message
              : 'Could not refresh capture status',
          );
        });
    }, 1000);
    return () => window.clearInterval(timer);
  }, [incidentId, latestJob, onError, onPacketCapturesChange, running]);

  const elapsedSeconds = useMemo(() => {
    if (!latestJob || !running) {
      return 0;
    }
    const started = latestJob.startedAt ? new Date(latestJob.startedAt).getTime() : now;
    return Math.max(0, Math.min(latestJob.durationSeconds, Math.floor((now - started) / 1000)));
  }, [latestJob, now, running]);

  const openDialog = async () => {
    try {
      onError(null);
      const [previewResponse, healthResponse] = await Promise.all([
        aiopsApi.getPacketCapturePreview(incidentId),
        aiopsApi.getPacketCaptureProviderHealth().catch(() => null),
      ]);
      setPreview(previewResponse);
      setHealth(healthResponse);
      setDialogOpen(true);
    } catch (requestError) {
      onError(
        requestError instanceof Error ? requestError.message : 'Could not prepare capture dialog',
      );
    }
  };

  const startCapture = async () => {
    if (!preview?.liveCaptureAllowed) {
      return;
    }
    try {
      setStarting(true);
      onError(null);
      const job = await aiopsApi.startPacketCapture(incidentId, {
        durationSeconds: preview.durationSeconds,
        capturePointId: preview.capturePointId,
      });
      setJobs((current) => [job, ...current.filter((item) => item.id !== job.id)]);
      setDialogOpen(false);
      if (job.status === 'COMPLETED') {
        const captures = await aiopsApi.getIncidentPacketCaptures(incidentId);
        onPacketCapturesChange(captures);
      }
    } catch (requestError) {
      onError(
        requestError instanceof Error ? requestError.message : 'Capture could not be started',
      );
    } finally {
      setStarting(false);
    }
  };

  const retry = () => {
    void openDialog();
  };

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1.5}>
        <Typography variant="h5">Packet Capture</Typography>
        {canCapture && (
          <Stack direction="row" spacing={1}>
            <IconActionButton
              icon="mdi:radar"
              label="Capture traffic"
              onClick={() => void openDialog()}
              disabled={running || liveCaptureDisabled}
              color="primary"
            />
            {onUploadClick && (
              <IconActionButton
                icon="mdi:upload-network-outline"
                label={uploadingPcap ? 'Uploading packet capture...' : 'Upload packet capture'}
                onClick={onUploadClick}
                disabled={uploadingPcap}
                color="secondary"
              />
            )}
          </Stack>
        )}
      </Stack>
      {liveCaptureDisabled && (
        <Alert severity="info">
          Live capture is disabled for resolved incidents. Historical packet captures remain
          available.
        </Alert>
      )}
      {!latestJob && (
        <Typography color="text.secondary">No packet capture attached yet.</Typography>
      )}
      {latestJob && (
        <Box
          sx={{
            p: 2,
            borderRadius: 1.5,
            border: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Stack spacing={1}>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
              {running && <CircularProgress size={18} />}
              <Typography variant="subtitle1">
                {isAutomatic(latestJob)
                  ? latestJob.trigger === 'AUTO_ROLLING'
                    ? 'Automatic forensic capture'
                    : 'Automatic capture'
                  : 'Investigation capture'}
              </Typography>
              <Chip size="small" label={triggerLabel(latestJob)} />
              <Chip size="small" color={jobChipColor(latestJob.status)} label={latestJob.status} />
            </Stack>
            {latestJob.trigger === 'AUTO_ROLLING' && (
              <Typography variant="body2" color="text.secondary">
                {latestJob.preTriggerSeconds ?? 60}s before / {latestJob.postTriggerSeconds ?? 20}s
                after detection
              </Typography>
            )}
            {latestJob.trigger === 'AUTO_INCIDENT' && (
              <Typography variant="body2" color="text.secondary">
                Post-trigger window: {latestJob.postTriggerSeconds ?? latestJob.durationSeconds} sec
              </Typography>
            )}
            {running && (
              <>
                <Typography variant="body2" color="text.secondary">
                  {elapsedSeconds} / {latestJob.durationSeconds} seconds
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(
                    100,
                    (elapsedSeconds / Math.max(1, latestJob.durationSeconds)) * 100,
                  )}
                />
              </>
            )}
            <Typography variant="body2" color="text.secondary">
              Capture point: {latestJob.capturePoint || 'BANK-FW-01 / WAN'}
            </Typography>
            {latestJob.status === 'COMPLETED' && (
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} flexWrap="wrap">
                <PcapSizeMetric
                  label="Relevant packets"
                  value={String(latestJob.packetCount ?? 0)}
                />
                <PcapSizeMetric
                  label="PCAP file size"
                  value={formatByteCount(latestJob.fileSizeBytes)}
                  hint={PCAP_FILE_SIZE_HINT}
                />
                {latestJob.analysisId ? (
                  <Typography variant="body2" color="text.secondary" alignSelf="flex-end">
                    Analysis available
                  </Typography>
                ) : null}
              </Stack>
            )}
            {(latestJob.status === 'FAILED' || latestJob.status === 'CANCELLED') && (
              <Alert
                severity="error"
                action={
                  canCapture && !liveCaptureDisabled ? (
                    <Button color="inherit" size="small" onClick={retry}>
                      Retry capture
                    </Button>
                  ) : null
                }
              >
                {isAutomatic(latestJob) ? 'Automatic capture failed. ' : ''}
                {latestJob.errorMessage || 'Capture failed'}
                {latestJob.failureCode ? ` (${latestJob.failureCode})` : ''}
              </Alert>
            )}
          </Stack>
        </Box>
      )}
      {packetCaptures.length === 0 && !latestJob && (
        <Typography color="text.secondary">
          Start an automated capture, or use manual upload below as a fallback.
        </Typography>
      )}
      <Dialog
        open={dialogOpen}
        onClose={() => !starting && setDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Capture traffic</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            {health && !health.available && (
              <Alert severity="warning">{health.message || 'Capture sensor is unavailable'}</Alert>
            )}
            {preview && !preview.liveCaptureAllowed && (
              <Alert severity="info">{preview.message}</Alert>
            )}
            <Stack spacing={0.25}>
              <Typography variant="caption" color="text.secondary">
                Source
              </Typography>
              <Typography>{preview?.sourceIp || '-'}</Typography>
            </Stack>
            <Stack spacing={0.25}>
              <Typography variant="caption" color="text.secondary">
                Target
              </Typography>
              <Typography>
                {preview?.destinationComponentName
                  ? `${preview.destinationComponentName}`
                  : 'External / unresolved'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {preview?.matchedInterfaceIp || preview?.destinationIp || '-'}
              </Typography>
            </Stack>
            <Stack spacing={0.25}>
              <Typography variant="caption" color="text.secondary">
                Capture point
              </Typography>
              <Typography>{preview?.capturePoint || '-'}</Typography>
            </Stack>
            <Stack spacing={0.25}>
              <Typography variant="caption" color="text.secondary">
                Duration
              </Typography>
              <Typography>{preview?.durationSeconds ?? 20} seconds</Typography>
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={starting}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={() => void startCapture()}
            disabled={starting || !preview?.liveCaptureAllowed}
          >
            {starting ? 'Starting…' : 'Start capture'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};

export default PacketCapturePanel;
