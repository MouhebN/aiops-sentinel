import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import pxToRem from 'theme/functions/pxToRem';

const ListItemIconComponent: Components<Omit<Theme, 'components'>>['MuiListItemIcon'] = {
  defaultProps: {},
  styleOverrides: {
    root: ({ theme }) => ({
      color: 'inherit',
      minWidth: pxToRem(36),
      borderRadius: theme.shape.borderRadius * 2,

      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.easeInOut,
        duration: theme.transitions.duration.standard,
      }),
    }),
  },
};

export default ListItemIconComponent;
