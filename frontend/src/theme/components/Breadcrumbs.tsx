import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const BreadcrumbsComponent: Components<Omit<Theme, 'components'>>['MuiBreadcrumbs'] = {
  styleOverrides: {
    li: {
      lineHeight: 0,
    },
    separator: ({ theme }) => ({
      fontSize: theme.typography.pxToRem(14),
      color: theme.palette.common.white,
    }),
    ol: {
      alignItems: 'flex-start',
    },
  },
};

export default BreadcrumbsComponent;
