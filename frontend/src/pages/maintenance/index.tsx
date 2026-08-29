import { Alert, Box, Paper, Stack, TextField, Typography } from '@mui/material';
import { aiopsApi } from 'api/aiopsApi';
import ApiState from 'components/aiops/ApiState';
import IconActionButton from 'components/aiops/IconActionButton';
import PageHeader from 'components/common/PageHeader';
import { useState } from 'react';

const MaintenancePage = () => {
  const [purgeDays, setPurgeDays] = useState(30);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const runPurge = async () => {
    const confirmationMessage = `Delete events older than ${purgeDays} days? This action cannot be undone.`;
    if (!window.confirm(confirmationMessage)) {
      return;
    }
    try {
      setLoading(true);
      setError(null);
      setMessage(null);
      const deletedCount = await aiopsApi.purgeEventsOlderThan(purgeDays);
      setMessage(`Purged ${deletedCount} events older than ${purgeDays} days.`);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Event purge failed');
    } finally {
      setLoading(false);
    }
  };

  const resetDemoData = async () => {
    if (
      !window.confirm(
        'Reset demo data? This action cannot be undone. Reports, packet captures, incidents, and events will be deleted.',
      )
    ) {
      return;
    }
    try {
      setLoading(true);
      setError(null);
      setMessage(null);
      await aiopsApi.resetDemoData();
      setMessage('Demo data reset completed.');
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Demo reset failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader>Maintenance</PageHeader>
      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} />
        {message && <Alert severity="success">{message}</Alert>}

        <Paper
          sx={{ p: 3, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Stack spacing={2}>
            <Typography variant="h5">Demo Cleanup</Typography>
            <Typography color="text.secondary">
              Use these actions to reset the dashboard before a demo. This action cannot be undone.
            </Typography>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              spacing={2}
              alignItems={{ md: 'flex-end' }}
            >
              <TextField
                type="number"
                label="Purge events older than"
                value={purgeDays}
                onChange={(event) => setPurgeDays(Number(event.target.value) || 0)}
                inputProps={{ min: 1 }}
                size="small"
                sx={{ maxWidth: 220 }}
                helperText="Days"
              />
              <IconActionButton
                icon="mdi:delete-sweep-outline"
                label="Purge old events"
                onClick={runPurge}
                color="warning"
              />
            </Stack>
            <Stack direction="row" justifyContent="flex-start">
              <IconActionButton
                icon="mdi:trash-can-outline"
                label="Reset demo data"
                onClick={resetDemoData}
                color="error"
              />
            </Stack>
          </Stack>
        </Paper>
      </Stack>
    </Box>
  );
};

export default MaintenancePage;
