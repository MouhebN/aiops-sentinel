import { Theme } from '@mui/material';
import { Components } from '@mui/material/styles/components';
import IconifyIcon from 'components/base/IconifyIcon';

const CustomIcon = (props: JSX.Element) => (
  <IconifyIcon
    icon="iconamoon:arrow-down-2-light"
    sx={{ ml: 1, width: 24, height: 24 }}
    {...props}
  />
);
const SelectComponent: Components<Omit<Theme, 'components'>>['MuiSelect'] = {
  defaultProps: {
    IconComponent: CustomIcon,
  },
  styleOverrides: {
    root: {
      '.MuiSvgIcon-root': {
        color: 'red',
      },
    },
    icon: {
      top: 'calc(50% - 0.85em)',
    },
    select: {
      '&:focus': {
        backgroundColor: 'transparent',
      },
    },
    iconOutlined: ({ theme }) => ({
      color: theme.palette.action.disabledBackground,
      top: 'calc(50% - 0.85em)',
    }),
    outlined: {
      border: 'none',
    },
    iconFilled: {
      top: 'calc(50% - 0.85em)',
    },
  },
};

export default SelectComponent;
