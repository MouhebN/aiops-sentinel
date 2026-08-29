import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import pxToRem from 'theme/functions/pxToRem';

const LinearProgressComponent: Components<Omit<Theme, 'components'>>['MuiLinearProgress'] = {
  styleOverrides: {
    root: ({ ownerState, theme }) => ({
      ...(ownerState.variant === 'determinate' && {
        height: pxToRem(3),
        borderRadius: theme.shape.borderRadius * 2,
        overflow: 'visible',
        position: 'relative',
      }),
    }),

    colorPrimary: ({ theme }) => ({
      backgroundColor: theme.palette.action.selected,
    }),

    colorSecondary: ({ theme }) => ({
      backgroundColor: theme.palette.action.selected,
    }),

    bar: ({ theme }) => ({
      height: '100%',
      borderRadius: theme.shape.borderRadius,
      position: 'absolute',
      transform: `translate(0, 0) !important`,
      transition: 'width 0.6s ease !important',
    }),
  },
};

export default LinearProgressComponent;
