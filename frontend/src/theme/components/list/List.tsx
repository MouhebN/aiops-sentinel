import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const ListComponent: Components<Omit<Theme, 'components'>>['MuiList'] = {
  styleOverrides: {
    padding: ({ theme }) => ({
      paddingTop: 0,
      paddingBottom: theme.spacing(1),
    }),
  },
};

export default ListComponent;
