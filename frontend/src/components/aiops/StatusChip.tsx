import { Chip } from '@mui/material';
import { statusColor } from 'helpers/aiops';
import { DeviceStatus } from 'types/aiops';

interface StatusChipProps {
  status: DeviceStatus;
}

const StatusChip = ({ status }: StatusChipProps) => (
  <Chip label={status} color={statusColor(status)} size="small" />
);

export default StatusChip;
