import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const PopoverComponent: Components<Omit<Theme, 'components'>>['MuiPopover'] = {
  styleOverrides: {
    paper: ({ theme }) => ({
      padding: 0,
      marginTop: theme.spacing(1),
      marginLeft: theme.spacing(0.75),
      backgroundColor: theme.palette.background.paper,
      borderRadius: theme.shape.borderRadius,
      boxShadow: theme.shadows[6],
    }),
  },
};

export default PopoverComponent;
