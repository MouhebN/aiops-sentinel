import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const ToolbarComponent: Components<Omit<Theme, 'components'>>['MuiToolbar'] = {
  styleOverrides: {
    regular: ({ theme }) => ({
      height: theme.spacing(8.5),
      minHeight: theme.spacing(8.5),
      paddingLeft: theme.spacing(3),
    }),
  },
};

export default ToolbarComponent;
