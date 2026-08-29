import { Chip } from '@mui/material';
import { severityColor } from 'helpers/aiops';
import { Severity } from 'types/aiops';

interface SeverityChipProps {
  severity: Severity;
}

const SeverityChip = ({ severity }: SeverityChipProps) => (
  <Chip label={severity} color={severityColor(severity)} size="small" />
);

export default SeverityChip;
