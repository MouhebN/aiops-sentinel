import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const ListItemTextComponent: Components<Omit<Theme, 'components'>>['MuiListItemText'] = {
  defaultProps: {},
  styleOverrides: {
    root: ({ theme }) => ({
      color: 'inherit',

      [theme.breakpoints.up('xl')]: {
        maxWidth: '100%',
        transition: theme.transitions.create(['opacity', 'margin'], {
          easing: theme.transitions.easing.easeInOut,
          duration: theme.transitions.duration.standard,
        }),
      },

      '& span': {
        fontSize: theme.typography.pxToRem(14),
        lineHeight: 1.4,
      },
    }),
  },
};

export default ListItemTextComponent;
