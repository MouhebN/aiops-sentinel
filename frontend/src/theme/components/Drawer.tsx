import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import pxToRem from 'theme/functions/pxToRem';

const DrawerComponent: Components<Omit<Theme, 'components'>>['MuiDrawer'] = {
  styleOverrides: {
    root: {
      width: pxToRem(270),
      whiteSpace: 'nowrap',
      flexShrink: 0,
    },
    paper: ({ theme }) => ({
      width: pxToRem(270),
      backgroundColor: theme.palette.transparent.main,
      transition: theme.transitions.create('width', {
        duration: theme.transitions.duration.shortest,
      }),
      border: 'none',
    }),
    // opens from the left
    paperAnchorDockedLeft: {},
  },
};

export default DrawerComponent;
