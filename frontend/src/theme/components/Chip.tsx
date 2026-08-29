import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import typography from 'theme/typography';

const ChipComponent: Components<Omit<Theme, 'components'>>['MuiChip'] = {
  styleOverrides: {
    root: ({ theme }) => ({
      ...typography.subtitle1,
      height: 'auto',
      padding: theme.spacing(0.5, 1),
      backgroundColor: theme.palette.grey[200],
      whiteSpace: 'nowrap',
      verticalAlign: 'baseline',
      borderRadius: theme.shape.borderRadius,
      position: 'relative',
    }),
    colorPrimary: ({ theme }) => ({
      backgroundColor: theme.palette.primary.main,
      color: theme.palette.common.white,
    }),
    colorSuccess: ({ theme }) => ({
      backgroundColor: theme.palette.success.light,
      color: theme.palette.success.dark,
    }),
    colorError: ({ theme }) => ({
      backgroundColor: theme.palette.error.light,
    }),
    label: {
      padding: 0,
    },
  },
};

export default ChipComponent;
