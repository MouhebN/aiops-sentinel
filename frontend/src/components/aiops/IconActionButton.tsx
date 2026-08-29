import { Box, IconButton, IconButtonProps, Tooltip } from '@mui/material';
import IconifyIcon from 'components/base/IconifyIcon';

interface IconActionButtonProps extends Omit<IconButtonProps, 'children'> {
  icon: string;
  label: string;
}

const IconActionButton = ({ icon, label, size = 'small', sx, ...rest }: IconActionButtonProps) => (
  <Tooltip title={label}>
    <Box component="span" sx={{ display: 'inline-flex' }}>
      <IconButton
        aria-label={label}
        size={size}
        sx={{
          width: size === 'small' ? 34 : 40,
          height: size === 'small' ? 34 : 40,
          borderRadius: 1,
          border: '1px solid',
          borderColor: 'divider',
          ...sx,
        }}
        {...rest}
      >
        <IconifyIcon icon={icon} fontSize={size === 'small' ? '1.15rem' : '1.3rem'} />
      </IconButton>
    </Box>
  </Tooltip>
);

export default IconActionButton;
