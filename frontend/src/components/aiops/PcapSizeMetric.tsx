import { Box, Stack, Tooltip, Typography } from '@mui/material';
import IconifyIcon from 'components/base/IconifyIcon';

export const PCAP_FILE_SIZE_HINT = 'Size of the saved capture file, including PCAP metadata.';

export const CAPTURED_PACKET_BYTES_HINT = 'Total size of the packets represented in the analysis.';

export const formatByteCount = (value?: number | null) => {
  if (value == null) {
    return '-';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
};

export const capturedPacketBytesOf = (capture: {
  capturedPacketBytes?: number;
  totalBytes: number;
}) => capture.capturedPacketBytes ?? capture.totalBytes;

interface PcapSizeMetricProps {
  label: string;
  value: string;
  hint?: string;
}

const PcapSizeMetric = ({ label, value, hint }: PcapSizeMetricProps) => (
  <Stack spacing={0.25}>
    <Stack direction="row" spacing={0.5} alignItems="center">
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      {hint ? (
        <Tooltip title={hint}>
          <Box
            component="span"
            sx={{ display: 'inline-flex', cursor: 'help', color: 'text.secondary' }}
          >
            <IconifyIcon icon="mdi:information-outline" fontSize="0.95rem" />
          </Box>
        </Tooltip>
      ) : null}
    </Stack>
    <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
      {value}
    </Typography>
  </Stack>
);

export default PcapSizeMetric;
