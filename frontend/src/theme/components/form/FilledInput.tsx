import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import pxToRem from 'theme/functions/pxToRem';

const FilledInputComponent: Components<Omit<Theme, 'components'>>['MuiFilledInput'] = {
  styleOverrides: {
    root: ({ theme }) => ({
      borderRadius: theme.shape.borderRadius,
      '& fieldset': {
        border: 'none',
      },
      '&:before, &:after': {
        display: 'none',
      },
    }),

    input: ({ theme }) => ({
      fontSize: theme.typography.pxToRem(14),
      height: pxToRem(22),
      paddingTop: theme.spacing(1.5),
      paddingRight: 1,
      paddingBottom: theme.spacing(1.5),
    }),
    inputSizeSmall: ({ theme }) => ({
      paddingTop: theme.spacing(0.75),
      paddingBottom: theme.spacing(0.75),
    }),
    adornedStart: ({ theme }) => ({
      color: theme.palette.text.secondary,
    }),
    adornedEnd: ({ theme }) => ({
      color: theme.palette.text.secondary,
      fontSize: theme.typography.pxToRem(1),
    }),
  },
};

export default FilledInputComponent;
