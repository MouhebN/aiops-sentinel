import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';

const PaperComponent: Components<Omit<Theme, 'components'>>['MuiPaper'] = {
  styleOverrides: {
    root: {
      backgroundClip: 'border-box',
      overflowWrap: 'break-word',
      margin: 0,
      padding: 0,
    },
    rounded: ({ theme }) => ({
      borderRadius: theme.shape.borderRadius * 4,
    }),
  },
};

export default PaperComponent;
