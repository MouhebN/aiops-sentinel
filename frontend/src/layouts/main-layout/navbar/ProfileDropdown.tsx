import {
  Avatar,
  Box,
  Button,
  ButtonBase,
  ListItemIcon,
  Menu,
  MenuItem,
  Stack,
  Typography,
} from '@mui/material';
import { useAuth } from 'auth/AuthContext';
import AvatarImage from 'assets/images/avatar.svg';
import IconifyIcon from 'components/base/IconifyIcon';
import { profileOptions } from 'data/navbar/menu-data';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import paths from 'routes/path';

const ProfileDropdown = () => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);
  const open = Boolean(anchorEl);
  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleLogout = async () => {
    handleClose();
    await logout();
    navigate(paths.login, { replace: true });
  };

  return (
    <Box
      sx={{
        px: 0.75,
        pr: 2,
      }}
    >
      <ButtonBase disableRipple={true} onClick={handleClick}>
        <Stack
          spacing={1.5}
          direction="row"
          alignItems="center"
          sx={{
            py: 0.75,
            ml: 0.75,
          }}
        >
          <Avatar
            alt="avatar"
            variant="rounded"
            src={AvatarImage}
            sx={{
              height: 36,
              width: 36,
            }}
          />
          <Typography
            variant="subtitle1"
            sx={{
              display: { xs: 'none', sm: 'block' },
            }}
          >
            {user?.fullName || 'User'}
          </Typography>
        </Stack>
      </ButtonBase>
      <Menu
        keepMounted
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
        slotProps={{
          paper: {
            style: {
              paddingTop: '8px',
              width: '100%',
              maxWidth: 120,
            },
          },
        }}
      >
        {profileOptions.map((option) => (
          <MenuItem
            key={option.id}
            sx={{
              py: 1,
              px: 1.5,
            }}
            onClick={handleClose}
          >
            <ListItemIcon sx={{ '&.MuiListItemIcon-root': { minWidth: 2, mr: 1 } }}>
              <IconifyIcon width={16} height={16} icon={option.icon} />
            </ListItemIcon>
            <Typography variant="subtitle2"> {option.title}</Typography>
          </MenuItem>
        ))}
        <Stack spacing={0.25} sx={{ px: 1.5, pt: 1 }}>
          <Typography variant="caption" color="text.secondary">
            {user?.email}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {user?.role}
          </Typography>
        </Stack>
        <Stack direction="row" sx={{ width: 1, justifyContent: 'center' }}>
          <Button
            size="small"
            variant="outlined"
            onClick={handleLogout}
            sx={{
              mt: 1.5,
              width: '80%',
              py: 0.5,
            }}
          >
            Logout
          </Button>
        </Stack>
      </Menu>
    </Box>
  );
};
export default ProfileDropdown;
