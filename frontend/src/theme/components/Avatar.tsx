import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const AvatarComponent: Components<Omit<Theme, 'components'>>['MuiAvatar'] = {
  styleOverrides: {
    root: ({ theme }) => ({
      boxShadow: theme.shadows[0],
      width: theme.spacing(5),
      height: theme.spacing(5),
      borderRadius: theme.shape.borderRadius,
    }),
    rounded: ({ theme }) => ({
      borderRadius: theme.shape.borderRadius * 5,
      backgroundColor: theme.palette.neutral.main,
    }),
  },
};

export default AvatarComponent;
