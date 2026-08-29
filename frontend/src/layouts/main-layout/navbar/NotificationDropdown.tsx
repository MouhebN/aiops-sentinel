import {
  Alert,
  Badge,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  Menu,
  Stack,
  Switch,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import { aiopsApi, Incident } from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import { canAcknowledgeIncidents } from 'auth/permissions';
import IconifyIcon from 'components/base/IconifyIcon';
import NotificationIcon from 'components/icons/NotificationIcon';
import SeverityChip from 'components/aiops/SeverityChip';
import StatusChip from 'components/aiops/StatusChip';
import { formatEventType } from 'helpers/aiops';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import SimpleBar from 'simplebar-react';

type AudioWindow = Window &
  typeof globalThis & {
    webkitAudioContext?: typeof AudioContext;
  };

const SEEN_STORAGE_KEY = 'aiops-seen-notification-incident-ids';
const SOUND_STORAGE_KEY = 'aiops-notification-sound-enabled';

function notificationsLabel(count: number) {
  if (count === 0) {
    return 'no active incident notifications';
  }
  if (count > 99) {
    return 'more than 99 active incident notifications';
  }
  return `${count} active incident notifications`;
}

const loadSeenIds = () => {
  try {
    const stored = window.localStorage.getItem(SEEN_STORAGE_KEY);
    return new Set(stored ? (JSON.parse(stored) as number[]) : []);
  } catch {
    return new Set<number>();
  }
};

const loadSoundEnabled = () => window.localStorage.getItem(SOUND_STORAGE_KEY) !== 'false';

const saveSeenIds = (ids: Set<number>) => {
  window.localStorage.setItem(SEEN_STORAGE_KEY, JSON.stringify(Array.from(ids)));
};

const ring = () => {
  const audioWindow = window as AudioWindow;
  const AudioContextClass = audioWindow.AudioContext || audioWindow.webkitAudioContext;
  if (!AudioContextClass) {
    return;
  }

  const context = new AudioContextClass();
  const gain = context.createGain();
  gain.gain.setValueAtTime(0.0001, context.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.18, context.currentTime + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.42);
  gain.connect(context.destination);

  [0, 0.16].forEach((offset) => {
    const oscillator = context.createOscillator();
    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(880, context.currentTime + offset);
    oscillator.connect(gain);
    oscillator.start(context.currentTime + offset);
    oscillator.stop(context.currentTime + offset + 0.12);
  });

  window.setTimeout(() => {
    context.close().catch(() => undefined);
  }, 700);
};

const NotificationDropdown = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canAcknowledge = canAcknowledgeIncidents(user?.role);
  const initialized = useRef(false);
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [seenIds, setSeenIds] = useState<Set<number>>(() => loadSeenIds());
  const [soundEnabled, setSoundEnabled] = useState(loadSoundEnabled);
  const [error, setError] = useState<string | null>(null);
  const open = Boolean(anchorEl);

  const activeIncidents = useMemo(
    () =>
      incidents
        .filter((incident) => incident.status === 'ACTIVE' && !incident.acknowledged)
        .sort((a, b) => dayjs(b.lastSeenAt).valueOf() - dayjs(a.lastSeenAt).valueOf()),
    [incidents],
  );

  const unreadCount = activeIncidents.filter((incident) => !seenIds.has(incident.id)).length;
  const criticalCount = activeIncidents.filter(
    (incident) => incident.severity === 'CRITICAL',
  ).length;

  const refresh = useCallback(async () => {
    try {
      setError(null);
      const response = await aiopsApi.getIncidents();
      const nextActive = response.filter(
        (incident) => incident.status === 'ACTIVE' && !incident.acknowledged,
      );
      const hasNewIncident =
        initialized.current && nextActive.some((incident) => !seenIds.has(incident.id));
      setIncidents(response);
      if (hasNewIncident && soundEnabled) {
        ring();
      }
      initialized.current = true;
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Notification error');
    }
  }, [seenIds, soundEnabled]);

  useEffect(() => {
    refresh();
    const interval = window.setInterval(refresh, 5000);
    return () => window.clearInterval(interval);
  }, [refresh]);

  const markVisibleAsSeen = (visibleIncidents: Incident[]) => {
    const next = new Set(seenIds);
    visibleIncidents.forEach((incident) => next.add(incident.id));
    setSeenIds(next);
    saveSeenIds(next);
  };

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
    markVisibleAsSeen(activeIncidents);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleSoundToggle = (enabled: boolean) => {
    setSoundEnabled(enabled);
    window.localStorage.setItem(SOUND_STORAGE_KEY, String(enabled));
    if (enabled) {
      ring();
    }
  };

  const handleAcknowledge = async (incident: Incident) => {
    try {
      setError(null);
      const updated = await aiopsApi.acknowledgeIncident(incident.id);
      setIncidents((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Acknowledge failed');
    }
  };

  const openIncident = () => {
    handleClose();
    navigate('/incidents');
  };

  const openIncidentDetail = (incident: Incident) => {
    handleClose();
    navigate(`/incidents/${incident.id}`);
  };

  return (
    <>
      <Tooltip title="Active incident notifications">
        <IconButton
          aria-label={notificationsLabel(unreadCount)}
          color="inherit"
          onClick={handleClick}
          sx={{ color: criticalCount ? 'error.main' : 'grey.200' }}
        >
          <Badge color={criticalCount ? 'error' : 'primary'} badgeContent={unreadCount}>
            <NotificationIcon />
          </Badge>
        </IconButton>
      </Tooltip>

      <Menu
        id="aiops-notification-menu"
        keepMounted
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
        slotProps={{
          paper: {
            sx: {
              width: { xs: 340, sm: 430 },
              borderRadius: 2,
              border: '1px solid',
              borderColor: 'divider',
              overflow: 'hidden',
            },
          },
        }}
      >
        <Stack spacing={0} sx={{ bgcolor: 'background.paper' }}>
          <Stack direction="row" py={2} px={2.5} justifyContent="space-between" alignItems="center">
            <Stack spacing={0.25}>
              <Typography variant="h6">Incident Notifications</Typography>
              <Typography variant="caption" color="text.secondary">
                {activeIncidents.length} active unacknowledged
              </Typography>
            </Stack>
            <Stack direction="row" spacing={1} alignItems="center">
              {criticalCount > 0 && (
                <Chip label={`${criticalCount} critical`} color="error" size="small" />
              )}
              <Tooltip title={soundEnabled ? 'Sound enabled' : 'Sound muted'}>
                <Switch
                  size="small"
                  checked={soundEnabled}
                  onChange={(event) => handleSoundToggle(event.target.checked)}
                />
              </Tooltip>
            </Stack>
          </Stack>

          <Divider />

          {error && (
            <Box px={2} pt={2}>
              <Alert severity="error">{error}</Alert>
            </Box>
          )}

          <SimpleBar style={{ maxHeight: 420 }}>
            <Stack spacing={1.25} p={1.5}>
              {activeIncidents.map((incident) => {
                const unread = !seenIds.has(incident.id);
                return (
                  <Box
                    key={incident.id}
                    sx={{
                      p: 1.5,
                      borderRadius: 1.5,
                      border: '1px solid',
                      borderColor: unread ? 'primary.main' : 'divider',
                      bgcolor: (theme) =>
                        unread
                          ? alpha(theme.palette.primary.main, 0.06)
                          : alpha(theme.palette.grey[100], 0.7),
                    }}
                  >
                    <Stack spacing={1.25}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Box
                          sx={{
                            width: 36,
                            height: 36,
                            borderRadius: 1.5,
                            display: 'grid',
                            placeItems: 'center',
                            color: incident.severity === 'CRITICAL' ? 'error.main' : 'warning.main',
                            bgcolor: (theme) =>
                              alpha(
                                incident.severity === 'CRITICAL'
                                  ? theme.palette.error.main
                                  : theme.palette.warning.main,
                                0.12,
                              ),
                            flexShrink: 0,
                          }}
                        >
                          <IconifyIcon
                            icon={
                              incident.severity === 'CRITICAL'
                                ? 'mdi:alert-octagon-outline'
                                : 'mdi:alert-outline'
                            }
                            fontSize="1.35rem"
                          />
                        </Box>
                        <Box minWidth={0} flex={1}>
                          <Stack direction="row" spacing={0.75} alignItems="center">
                            <Typography fontWeight={700} noWrap>
                              {incident.title}
                            </Typography>
                            {unread && <Chip size="small" label="New" color="primary" />}
                          </Stack>
                          <Typography variant="body2" color="text.secondary" noWrap>
                            {incident.deviceName} - {formatEventType(incident.category)}
                          </Typography>
                        </Box>
                      </Stack>

                      <Typography variant="body2" color="text.secondary">
                        {incident.latestEvent.message}
                      </Typography>

                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        <SeverityChip severity={incident.severity} />
                        <StatusChip status={incident.resultingStatus} />
                        <Chip
                          size="small"
                          label={dayjs(incident.lastActivityAt ?? incident.lastSeenAt).format(
                            'HH:mm:ss',
                          )}
                          variant="outlined"
                        />
                      </Stack>

                      <Stack direction="row" spacing={0.75} justifyContent="flex-end">
                        {canAcknowledge && (
                          <Tooltip title="Acknowledge incident">
                            <IconButton
                              size="small"
                              onClick={() => handleAcknowledge(incident)}
                              sx={{
                                width: 34,
                                height: 34,
                                borderRadius: 1,
                                color: 'success.main',
                                bgcolor: (theme) => alpha(theme.palette.success.main, 0.1),
                                '&:hover': {
                                  bgcolor: (theme) => alpha(theme.palette.success.main, 0.18),
                                },
                              }}
                            >
                              <IconifyIcon icon="mdi:check" fontSize="1.15rem" />
                            </IconButton>
                          </Tooltip>
                        )}
                        <Tooltip title="Open incident detail">
                          <IconButton
                            size="small"
                            onClick={() => openIncidentDetail(incident)}
                            sx={{
                              width: 34,
                              height: 34,
                              borderRadius: 1,
                              color: 'primary.main',
                              bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1),
                              '&:hover': {
                                bgcolor: (theme) => alpha(theme.palette.primary.main, 0.18),
                              },
                            }}
                          >
                            <IconifyIcon icon="mdi:arrow-right" fontSize="1.15rem" />
                          </IconButton>
                        </Tooltip>
                      </Stack>
                    </Stack>
                  </Box>
                );
              })}

              {!activeIncidents.length && !error && (
                <Stack alignItems="center" spacing={1.25} py={5}>
                  <Box
                    sx={{
                      width: 52,
                      height: 52,
                      borderRadius: 2,
                      display: 'grid',
                      placeItems: 'center',
                      color: 'success.main',
                      bgcolor: (theme) => alpha(theme.palette.success.main, 0.12),
                    }}
                  >
                    <IconifyIcon icon="mdi:check-circle-outline" fontSize="1.7rem" />
                  </Box>
                  <Typography fontWeight={700}>No active unacknowledged incidents</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Critical operational notifications will appear here.
                  </Typography>
                </Stack>
              )}
            </Stack>
          </SimpleBar>

          <Divider />

          <Stack direction="row" spacing={1} p={1.5} alignItems="center">
            <Tooltip title="Refresh notifications">
              <IconButton
                onClick={refresh}
                sx={{
                  width: 42,
                  height: 42,
                  borderRadius: 1.25,
                  border: '1px solid',
                  borderColor: 'divider',
                }}
              >
                <IconifyIcon icon="mdi:refresh" fontSize="1.25rem" />
              </IconButton>
            </Tooltip>
            <Button
              fullWidth
              variant="contained"
              onClick={openIncident}
              startIcon={<IconifyIcon icon="mdi:clipboard-alert-outline" />}
            >
              Incidents
            </Button>
          </Stack>
        </Stack>
      </Menu>
    </>
  );
};

export default NotificationDropdown;
