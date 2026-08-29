import { Paper, Stack, Typography } from '@mui/material';
import IconifyIcon from 'components/base/IconifyIcon';

interface MetricCardProps {
  title: string;
  value: number | string;
  icon: string;
  color: string;
}

const MetricCard = ({ title, value, icon, color }: MetricCardProps) => (
  <Paper
    elevation={0}
    sx={{
      p: 2.5,
      borderRadius: 2,
      border: '1px solid',
      borderColor: 'divider',
      height: 1,
    }}
  >
    <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
      <Stack spacing={0.5}>
        <Typography variant="body2" color="text.secondary">
          {title}
        </Typography>
        <Typography variant="h3">{value}</Typography>
      </Stack>
      <Stack
        alignItems="center"
        justifyContent="center"
        sx={{
          width: 44,
          height: 44,
          borderRadius: 1.5,
          color,
          bgcolor: `${color}1A`,
        }}
      >
        <IconifyIcon icon={icon} fontSize="1.5rem" />
      </Stack>
    </Stack>
  </Paper>
);

export default MetricCard;
