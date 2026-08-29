import { alpha, Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const CardComponent: Components<Omit<Theme, 'components'>>['MuiCard'] = {
  styleOverrides: {
    root: ({ theme }) => ({
      display: 'flex',
      flexDirection: 'column',
      position: 'relative',
      minWidth: 0,
      wordWrap: 'break-word',
      backgroundColor: theme.palette.common.white,
      backgroundClip: 'border-box',
      border: `${theme.spacing(0)} solid ${alpha(theme.palette.common.black, 0.125)}`,
      borderRadius: theme.shape.borderRadius * 1.5,
      boxShadow: theme.shadows[2],
    }),
  },
};

export default CardComponent;
