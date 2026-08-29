import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import pxToRem from 'theme/functions/pxToRem';

const ListItemButtonComponent: Components<Omit<Theme, 'components'>>['MuiListItemButton'] = {
  defaultProps: {
    disableGutters: false,
    disableRipple: true,
  },
  styleOverrides: {
    root: ({ theme }) => ({
      whiteSpace: 'nowrap',

      padding: theme.spacing(1, 1.25),
      marginBottom: pxToRem(2),
      borderRadius: theme.shape.borderRadius * 2,
      backgroundColor: 'inherit',
      color: theme.palette.text.secondary,
      paddingLeft: '10px',
      '&:hover,  &:focus': {
        backgroundColor: theme.palette.primary.light,
        color: theme.palette.primary.main,
      },
      '&.Mui-selected': {
        color: 'white',
        backgroundColor: theme.palette.primary.main,
        '&:hover': {
          backgroundColor: theme.palette.primary.main,
          color: 'white',
        },
      },
    }),
  },
};

export default ListItemButtonComponent;
