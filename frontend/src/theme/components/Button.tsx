import { Components, Theme } from '@mui/material/styles';

declare module '@mui/material/Button' {
  interface ButtonPropsColorOverrides {
    neutral: true;
  }
  interface IconButtonPropsColorOverrides {
    neutral: true;
  }
}

export const ButtonComponent: Components<Omit<Theme, 'components'>>['MuiButton'] = {
  defaultProps: {
    disableElevation: true,
  },
  styleOverrides: {
    root: ({ theme }) => ({
      ...theme.typography.button,
      borderRadius: theme.shape.borderRadius,
      padding: theme.spacing(1, 3),
    }),
    fullWidth: ({ theme }) => ({
      padding: theme.spacing(1.5, 3),
    }),

    textSizeLarge: ({ theme }) => ({
      ...theme.typography.button,
      padding: theme.spacing(1.5, 3),
    }),
    textSizeMedium: ({ theme }) => ({
      ...theme.typography.button,
      padding: theme.spacing(1.5, 3),
    }),
    textSizeSmall: ({ theme }) => ({
      ...theme.typography.subtitle1,
      padding: theme.spacing(1, 2.5),
    }),
    outlined: ({ theme }) => ({
      borderColor: theme.palette.text.disabled,
      '&.MuiButton-outlined.Mui-disabled': {
        backgroundColor: theme.palette.action.disabledBackground,
      },
    }),
    outlinedSecondary: ({ theme }) => ({
      backgroundColor: theme.palette.common.white,
      color: theme.palette.secondary.main,
      '&:hover': {
        backgroundColor: `${theme.palette.secondary.lighter}`,
        borderColor: `${theme.palette.secondary.main}`,
      },
    }),
    outlinedSizeLarge: ({ theme }) => ({
      ...theme.typography.button,
      padding: theme.spacing(1.5, 3),
    }),
    outlinedSizeMedium: ({ theme }) => ({
      ...theme.typography.button,
      padding: theme.spacing(1, 3),
    }),
    outlinedSizeSmall: ({ theme }) => ({
      ...theme.typography.subtitle1,
      padding: theme.spacing(1, 2.5),
    }),

    containedSizeSmall: ({ theme }) => ({
      ...theme.typography.subtitle1,
      padding: theme.spacing(1, 2.5),
    }),
    containedSizeMedium: ({ theme }) => ({
      ...theme.typography.button,
      padding: theme.spacing(1, 3),
    }),
    containedSizeLarge: ({ theme }) => ({
      ...theme.typography.button,
      padding: theme.spacing(1.5, 3),
    }),
    containedSecondary: ({ theme }) => ({
      color: theme.palette.primary.main,
      backgroundColor: theme.palette.primary.light,
      '&:hover': {
        backgroundColor: `${theme.palette.primary.lighter}`,
      },
    }),

    startIcon: {
      '& > *:first-of-type': {
        fontSize: 18,
      },
    },
    endIcon: {
      '& > *:first-of-type': {
        fontSize: 18,
      },
    },

    iconSizeSmall: {
      '& > *:first-of-type': {
        fontSize: 20,
      },
    },
    iconSizeMedium: {
      '& > *:first-of-type': {
        fontSize: 24,
      },
    },
    iconSizeLarge: {
      '& > *:first-of-type': {
        fontSize: 24,
      },
    },
  },
};
