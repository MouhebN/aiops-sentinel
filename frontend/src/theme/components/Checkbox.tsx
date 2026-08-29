import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import CheckboxCheckedIcon from 'components/icons/common/CheckboxChecked';
import CheckboxIcon from 'components/icons/common/CheckboxIcon';

const CheckboxComponent: Components<Omit<Theme, 'components'>>['MuiCheckbox'] = {
  defaultProps: {
    icon: <CheckboxIcon />,
    checkedIcon: <CheckboxCheckedIcon />,
  },
  styleOverrides: {
    root: ({ theme }) => ({
      '&:hover': {
        backgroundColor: theme.palette.transparent.main,
      },
      '& .iconify': {
        fill: theme.palette.transparent.main,
      },
      '& .MuiSvgIcon-root': {
        width: theme.spacing(2.5),
        height: theme.spacing(2.5),
        fill: theme.palette.transparent.main,
        borderRadius: theme.shape.borderRadius,
      },
      '&.Mui-disabled svg': {
        color: theme.palette.grey[500],
      },
    }),

    sizeSmall: {
      '& .iconify': {
        width: 20,
        height: 20,
      },
    },
    sizeMedium: {
      '& .iconify': {
        width: 25,
        height: 25,
      },
    },
  },
};

export default CheckboxComponent;
