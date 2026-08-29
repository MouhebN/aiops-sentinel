import { Box, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';

interface PageHeaderProps {
  children: ReactNode;
  actions?: ReactNode;
}

const PageHeader = ({ children, actions }: PageHeaderProps) => {
  return (
    <Stack
      direction="row"
      alignItems="center"
      sx={{
        pt: 1,
      }}
    >
      <Typography variant="h2">{children}</Typography>
      <Box flexGrow={1} />
      {actions}
    </Stack>
  );
};

export default PageHeader;
