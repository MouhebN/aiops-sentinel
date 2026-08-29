import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const BadgeComponent: Components<Omit<Theme, 'components'>>['MuiBadge'] = {
  styleOverrides: {
    badge: ({ theme }) => ({
      minWidth: theme.spacing(2),
      height: theme.spacing(2),
      padding: 0,
    }),
    anchorOriginTopRightRectangular: {
      transform: 'scale(1) translate(30%, -20%)',
    },
  },
};

export default BadgeComponent;
