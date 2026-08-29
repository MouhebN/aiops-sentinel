import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const InputComponent: Components<Omit<Theme, 'components'>>['MuiInput'] = {
  styleOverrides: {
    root: {
      '&:before, &:after': {
        display: 'none',
      },
    },
    input: { marginTop: 0 },
  },
};

export default InputComponent;
