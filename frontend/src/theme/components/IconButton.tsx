import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const IconButtonComponent: Components<Omit<Theme, 'components'>>['MuiIconButton'] = {
  styleOverrides: {
    root: ({ theme }) => ({
      borderRadius: theme.shape.borderRadius,
      '&:hover': {
        backgroundColor: theme.palette.transparent.main,
      },

      '& .MuiTypography-root': {
        display: 'none',

        [theme.breakpoints.up('sm')]: {
          display: 'inline-block',
          lineHeight: 1.2,
          marginLeft: theme.spacing(1),
        },
      },
    }),
    colorPrimary: ({ theme }) => ({
      backgroundColor: theme.palette.primary.main,
      color: theme.palette.common.white,
      '&:hover': { backgroundColor: theme.palette.primary.dark },
    }),
    colorSecondary: ({ theme }) => ({
      backgroundColor: theme.palette.primary.light,
      color: theme.palette.primary.main,
      '&:hover': { backgroundColor: theme.palette.primary.lighter },
    }),
    colorError: ({ theme }) => ({
      backgroundColor: theme.palette.error.light,
      color: theme.palette.error.main,
      '&:hover': { backgroundColor: theme.palette.error.lighter },
    }),
    colorWarning: ({ theme }) => ({
      backgroundColor: theme.palette.warning.light,
      color: theme.palette.warning.main,
      '&:hover': { backgroundColor: theme.palette.warning.lighter },
    }),
    sizeMedium: ({ theme }) => ({
      padding: theme.spacing(1),
    }),
    sizeLarge: ({ theme }) => ({
      fontSize: theme.typography.pxToRem(24),
    }),
    sizeSmall: ({ theme }) => ({
      fontSize: theme.typography.pxToRem(24),
      padding: theme.spacing(0.75),
    }),
  },
};

export default IconButtonComponent;
