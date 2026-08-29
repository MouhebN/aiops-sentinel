import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const InputAdornmentComponent: Components<Omit<Theme, 'components'>>['MuiInputAdornment'] = {
  styleOverrides: {
    root: ({ theme }) => ({
      '& .iconify': {
        flexShrink: 0,
        fontSize: theme.typography.pxToRem(24),
      },
    }),
  },
};

export default InputAdornmentComponent;
