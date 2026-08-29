import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const ListItemComponent: Components<Omit<Theme, 'components'>>['MuiListItem'] = {
  defaultProps: {
    disableGutters: true,
  },

  styleOverrides: {
    root: ({ theme }) => ({
      paddingTop: 0,
      paddingBottom: 0,
      cursor: 'pointer',
      borderRadius: theme.shape.borderRadius * 2,
    }),
  },
};

export default ListItemComponent;
