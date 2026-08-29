import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const InputLabelComponent: Components<Omit<Theme, 'components'>>['MuiInputLabel'] = {
  defaultProps: { shrink: true },
  styleOverrides: {
    root: ({ theme }) => ({
      position: 'relative',
      transform: 'none',
      color: theme.palette.text.secondary,
      paddingLeft: theme.spacing(0),
      paddingBottom: theme.spacing(0.6),
      paddingRight: 1,
      '& +.MuiInputBase-root': {
        marginTop: 0, // Remove Margin Top of The MuiInput right after  a label
      },
    }),
    focused: ({ theme }) => ({
      color: theme.palette.success.main,
    }),
  },
};

export default InputLabelComponent;
