import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const ListSubheaderComponent: Components<Omit<Theme, 'components'>>['MuiListSubheader'] = {
  defaultProps: {},
  styleOverrides: {
    root: ({ theme }) => ({
      ...theme.typography.body2,
      marginTop: theme.spacing(3),
      marginBottom: theme.spacing(0),
      color: theme.palette.action.active,
      backgroundColor: theme.palette.transparent.main,
      padding: theme.spacing(0.4, 1.5),
    }),
  },
};

export default ListSubheaderComponent;
