import { Alert, Stack, Typography } from '@mui/material';
import IconActionButton from 'components/aiops/IconActionButton';

interface ApiStateProps {
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
}

const ApiState = ({ loading, error, onRetry }: ApiStateProps) => {
  if (loading) {
    return <Typography color="text.secondary">Loading supervision data...</Typography>;
  }

  if (!error) {
    return null;
  }

  return (
    <Alert
      severity="error"
      action={
        onRetry ? (
          <IconActionButton color="inherit" icon="mdi:refresh" label="Retry" onClick={onRetry} />
        ) : null
      }
    >
      <Stack>
        <Typography fontWeight={600}>Backend API is not reachable</Typography>
        <Typography variant="body2">{error}</Typography>
      </Stack>
    </Alert>
  );
};

export default ApiState;
