import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const SwitchComponent: Components<Omit<Theme, 'components'>>['MuiSwitch'] = {
  styleOverrides: {
    root: {
      height: 18,
      width: 36,
      padding: 0,

      '& > .MuiSwitch-switchBase:first-of-type': { left: 3, top: 1 },
      '& .MuiSwitch-track': {
        borderRadius: 18,
        backgroundColor: '#E9E9EA',
        opacity: 1,
      },
    },
    switchBase: {
      padding: 0,
      '&.Mui-checked': {
        transform: 'translateX(18px)',
        color: '#fff',
        '& + .MuiSwitch-track': {
          backgroundColor: '#65C466',
          opacity: 1,
          border: 0,
        },
        '&.Mui-disabled + .MuiSwitch-track': {
          opacity: 0.5,
        },
      },
      '& .MuiSwitch-thumb': {
        width: 14,
        height: 14,
        marginTop: 1.5,
        paddingLeft: 8,
      },
      '& .MuiSwitch-input': {
        left: '-130%',
        width: '350%',
      },
    },
  },
};

export default SwitchComponent;
