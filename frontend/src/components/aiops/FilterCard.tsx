import { Paper, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';

interface FilterCardProps {
  title: string;
  children: ReactNode;
}

const FilterCard = ({ title, children }: FilterCardProps) => (
  <Paper
    className="filter-card"
    elevation={0}
    sx={{
      p: 2,
      borderRadius: 2,
      border: '1px solid',
      borderColor: 'divider',
    }}
  >
    <Stack spacing={2}>
      <Typography variant="h6">{title}</Typography>
      {children}
    </Stack>
  </Paper>
);

export default FilterCard;
