import {
  Box,
  Button,
  Divider,
  IconButton,
  InputAdornment,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import IconifyIcon from 'components/base/IconifyIcon';
import { useState } from 'react';

const PasswordResetForm = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const handleClickShowPassword = () => {
    setShowPassword((prevShowPassword) => !prevShowPassword);
  };

  const handleClickShowConfirmPassword = () => {
    setShowConfirmPassword((prevShowConfirmPassword) => !prevShowConfirmPassword);
  };
  return (
    <Box
      sx={{
        mt: { sm: 5, xs: 2.5 },
      }}
    >
      <Stack spacing={3}>
        <TextField
          fullWidth
          variant="outlined"
          id="password"
          type={showPassword ? 'text' : 'password'}
          label="Password"
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  aria-label="toggle password visibility"
                  onClick={handleClickShowPassword}
                  edge="end"
                >
                  {showPassword ? (
                    <IconifyIcon icon="el:eye-close" color="action.active" />
                  ) : (
                    <IconifyIcon icon="el:eye-open" color="action.focus" />
                  )}
                </IconButton>
              </InputAdornment>
            ),
          }}
        />
        <TextField
          fullWidth
          variant="outlined"
          id="password"
          type={showConfirmPassword ? 'text' : 'password'}
          label="Comfirm Password"
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  aria-label="toggle password visibility"
                  onClick={handleClickShowConfirmPassword}
                  edge="end"
                >
                  {showConfirmPassword ? (
                    <IconifyIcon icon="el:eye-close" color="action.active" />
                  ) : (
                    <IconifyIcon icon="el:eye-open" color="action.focus" />
                  )}
                </IconButton>
              </InputAdornment>
            ),
          }}
        />
      </Stack>
      <Button
        color="primary"
        variant="contained"
        size="large"
        fullWidth
        component={Link}
        href="#!"
        type="submit"
        sx={{ mt: 3 }}
      >
        Reset Password
      </Button>
      <Stack
        sx={{
          textAlign: 'center',
          color: 'text.secondary',
          my: 3,
        }}
      />
      <Divider
        sx={{
          my: 3,
        }}
      />

      <Stack
        spacing={1.5}
        sx={{
          mt: 4,
        }}
      >
        <Typography textAlign="center" color="text.secondary" variant="subtitle1">
          Remembered your Password?
        </Typography>
        <Button
          component={Link}
          href="/authentication/login"
          variant="outlined"
          sx={{ typography: { sm: 'button', xs: 'subtitle1', whiteSpace: 'nowrap' } }}
        >
          Back to Sign-in
        </Button>
      </Stack>
    </Box>
  );
};

export default PasswordResetForm;
