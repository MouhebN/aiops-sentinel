import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import { Icon } from '@iconify/react';
import SeverityChip from './SeverityChip';
import StatusChip from './StatusChip';
import { EventLog } from 'types/aiops';
import { formatDeviceType, formatEventType } from 'helpers/aiops';
import dayjs from 'dayjs';

const DetailItem = ({ label, value }: { label: string; value: string }) => (
  <Stack spacing={0.25}>
    <Typography variant="caption" color="text.secondary">
      {label}
    </Typography>
    <Typography sx={{ overflowWrap: 'anywhere' }}>{value}</Typography>
  </Stack>
);

interface EventDetailsDialogProps {
  event: EventLog | null;
  open: boolean;
  onClose: () => void;
}

const EventDetailsDialog = ({ event, open, onClose }: EventDetailsDialogProps) => (
  <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
    <DialogTitle>Event Details</DialogTitle>
    <DialogContent dividers>
      {event ? (
        <Stack spacing={2.5}>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            <SeverityChip severity={event.severity} />
            <StatusChip status={event.resultingStatus} />
            {event.eventSource === 'SYSLOG' && <Chip size="small" color="info" label="SYSLOG" />}
          </Stack>

          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <DetailItem label="Event" value={formatEventType(event.eventType)} />
            </Grid>
            <Grid item xs={12} md={6}>
              <DetailItem
                label="Occurred At"
                value={dayjs(event.occurredAt).format('DD/MM/YYYY HH:mm:ss')}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <DetailItem label="Device" value={event.deviceName} />
            </Grid>
            <Grid item xs={12} md={6}>
              <DetailItem label="Type" value={formatDeviceType(event.deviceType)} />
            </Grid>
            <Grid item xs={12} md={6}>
              <DetailItem label="Location" value={event.location} />
            </Grid>
            <Grid item xs={12} md={6}>
              <DetailItem label="Device ID" value={event.deviceId} />
            </Grid>
            {event.sourceIp && (
              <Grid item xs={12} md={6}>
                <DetailItem label="Source IP" value={event.sourceIp} />
              </Grid>
            )}
            {event.syslogSourceName && (
              <Grid item xs={12} md={6}>
                <DetailItem label="Syslog Source" value={event.syslogSourceName} />
              </Grid>
            )}
            {event.parsingProfile && (
              <Grid item xs={12} md={6}>
                <DetailItem label="Parser Profile" value={event.parsingProfile} />
              </Grid>
            )}
            {event.eventSource && (
              <Grid item xs={12} md={6}>
                <DetailItem label="Event Source" value={event.eventSource} />
              </Grid>
            )}
          </Grid>

          <Divider />

          <Box>
            <Typography variant="caption" color="text.secondary">
              Message
            </Typography>
            <Typography sx={{ mt: 0.5 }}>{event.message}</Typography>
          </Box>

          {event.details && (
            <Box>
              <Typography variant="caption" color="text.secondary">
                Details
              </Typography>
              <Typography sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>{event.details}</Typography>
            </Box>
          )}

          {event.rawLog && (
            <Accordion
              disableGutters
              elevation={0}
              sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}
            >
              <AccordionSummary expandIcon={<Icon icon="mdi:chevron-down" />}>
                <Typography variant="subtitle2">Raw Log</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <Box
                  component="pre"
                  sx={{
                    m: 0,
                    p: 1.5,
                    borderRadius: 1.5,
                    bgcolor: 'grey.100',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    fontSize: 13,
                    fontFamily: 'monospace',
                  }}
                >
                  {event.rawLog}
                </Box>
              </AccordionDetails>
            </Accordion>
          )}
        </Stack>
      ) : null}
    </DialogContent>
  </Dialog>
);

export default EventDetailsDialog;
