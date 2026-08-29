import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import pxToRem from 'theme/functions/pxToRem';

const InputBaseComponent: Components<Omit<Theme, 'components'>>['MuiInputBase'] = {
  styleOverrides: {
    root: {
      '& .Mui-focused': {
        outline: 0,
        borderColor: 'success.main',
        borderStyle: 'solid',
        boxShadow: '',
      },
    },
    input: ({ theme }) => ({
      height: pxToRem(22),
      paddingTop: theme.spacing(1.5),
      paddingRight: 1,
      paddingBottom: theme.spacing(1.5),
      margin: 0,
      '&::placeholder': {
        opacity: 1,
        color: theme.palette.grey[600],
      },
    }),
    inputSizeSmall: ({ theme }) => ({
      paddingTop: theme.spacing(0.75),
      paddingBottom: theme.spacing(0.75),
    }),
    multiline: ({ theme }) => ({
      padding: `${theme.typography.pxToRem(10)} ${theme.typography.pxToRem(12)}`,
    }),
  },
};

export default InputBaseComponent;
