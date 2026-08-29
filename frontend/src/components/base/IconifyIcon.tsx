import { Icon, IconProps } from '@iconify/react';
import { Box, BoxProps } from '@mui/material';

interface IconifyProps extends BoxProps {
  icon: IconProps['icon'];
}

const IconifyIcon = ({ icon, ...rest }: IconifyProps) => {
  return <Box component={Icon} icon={icon} {...rest} />;
};

export default IconifyIcon;
