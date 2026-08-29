import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import pxToRem from 'theme/functions/pxToRem';

const OutlinedInputComponent: Components<Omit<Theme, 'components'>>['MuiOutlinedInput'] = {
  defaultProps: {
    notched: false,
  },
  styleOverrides: {
    root: ({ theme, ownerState }) => ({
      color: theme.palette.grey[800],
      fontSize: theme.typography.pxToRem(14),
      fontWeight: theme.typography.fontWeightRegular,
      lineHeight: 20,

      '&:hover .MuiOutlinedInput-notchedOutline': {
        borderColor: `${theme.palette[ownerState.color!]?.main}` || theme.palette.primary.lighter,
      },
      '&.Mui-focused': {
        '& .MuiOutlinedInput-notchedOutline': {
          borderColor: `${theme.palette[ownerState.color!]?.main}` || theme.palette.primary.main,
        },
      },
    }),
    input: ({ theme }) => ({
      height: pxToRem(22),
      paddingTop: theme.spacing(1.5),
      paddingRight: 1,
      paddingBottom: theme.spacing(1.5),
    }),
    inputSizeSmall: ({ theme }) => ({
      paddingTop: theme.spacing(0.75),
      paddingBottom: theme.spacing(0.75),
    }),
    notchedOutline: ({ theme }) => ({
      borderColor: theme.palette.action.focus,
    }),
    colorSecondary: ({ theme }) => ({
      '& fieldset': {
        borderWidth: 1,
        borderStyle: 'solid',
        borderColor: theme.palette.secondary.main,
      },
      '&:hover fieldset': {
        borderColor: theme.palette.secondary.main,
      },
      '&.Mui-focused fieldset': {
        borderColor: theme.palette.secondary.main,
      },
    }),
  },
};

export default OutlinedInputComponent;
