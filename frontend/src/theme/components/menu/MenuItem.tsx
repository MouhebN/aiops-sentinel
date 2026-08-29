import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const MenuItemComponent: Components<Omit<Theme, 'components'>>['MuiMenuItem'] = {
  defaultProps: {},
  styleOverrides: {
    root: ({ theme }) => ({
      minHeight: 0,
      '&.Mui-selected': {
        backgroundColor: theme.palette.action.selected,
      },
      '&.Mui-selected:hover': {
        backgroundColor: theme.palette.action.selected,
      },
    }),
  },
};

export default MenuItemComponent;
