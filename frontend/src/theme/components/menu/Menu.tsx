import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const MenuComponent: Components<Omit<Theme, 'components'>>['MuiMenu'] = {
  defaultProps: {
    disableScrollLock: true,
  },
  styleOverrides: {
    paper: ({ theme }) => ({
      margin: 'auto',
      borderRadius: theme.spacing(0.75),
      marginTop: theme.spacing(1),
      color: theme.palette.text.secondary,
      backgroundColor: theme.palette.common.white,
      boxShadow: theme.shadows[6],
    }),
  },
};

export default MenuComponent;
