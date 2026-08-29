import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const DividerComponent: Components<Omit<Theme, 'components'>>['MuiDivider'] = {
  styleOverrides: {
    root: ({ theme }) => ({
      backgroundColor: theme.palette.action.selected,
      height: 1,
      margin: theme.spacing(2, 0),
      borderBottom: 0,
      borderLeft: 0,
      borderRight: 0,
    }),
  },
};

export default DividerComponent;
