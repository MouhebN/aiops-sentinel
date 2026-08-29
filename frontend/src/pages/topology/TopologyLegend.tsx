import { Box, Chip, Stack, Typography } from '@mui/material';
import StatusChip from 'components/aiops/StatusChip';

const TopologyLegend = () => (
  <Box
    sx={{
      position: 'absolute',
      left: 12,
      bottom: 12,
      zIndex: 5,
      px: 1.5,
      py: 1,
      borderRadius: 1,
      bgcolor: 'background.paper',
      border: '1px solid',
      borderColor: 'divider',
      maxWidth: { xs: 280, sm: 520 },
    }}
  >
    <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>
      Operational status stays separate from security state.
    </Typography>
    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
      <StatusChip status="UP" />
      <StatusChip status="WARNING" />
      <StatusChip status="DEGRADED" />
      <StatusChip status="DOWN" />
      <Chip size="small" color="error" label="UNDER ATTACK" />
      <Chip size="small" color="error" variant="outlined" label="TARGETED" />
      <Chip size="small" color="warning" label="SECURITY EVENT" />
    </Stack>
    <Typography variant="caption" color="text.secondary" display="block" mt={0.75}>
      Solid grey = infrastructure connection. Dashed red = active attack path.
    </Typography>
  </Box>
);

export default TopologyLegend;
