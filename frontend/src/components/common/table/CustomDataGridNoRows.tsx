import { Stack, Typography } from '@mui/material';
import Box from '@mui/material/Box';
import NoDataIcon from 'components/icons/NoDataIcon';

function CustomDataGridNoRows() {
  return (
    <Stack
      sx={{
        alignItems: 'center',
        justifyContent: 'center',
        height: 195,
        marginTop: 8,
      }}
    >
      <NoDataIcon
        sx={{
          width: 120,
          height: 120,
        }}
      />
      <Box
        sx={{
          mt: 1,
          mb: 2,
        }}
      >
        <Typography variant="subtitle2" color="text.secondary">
          No Data
        </Typography>
      </Box>
    </Stack>
  );
}

export default CustomDataGridNoRows;
