import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const MenuListComponent: Components<Omit<Theme, 'components'>>['MuiMenuList'] = {
  styleOverrides: {
    root: {
      flex: 1,
    },
  },
};

export default MenuListComponent;
