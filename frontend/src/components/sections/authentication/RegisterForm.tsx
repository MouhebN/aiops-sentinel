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

const RegisterForm = () => {
  const [showPassword, setShowPassword] = useState(false);
  const handleClickShowPassword = () => {
    setShowPassword((prevShowPassword) => !prevShowPassword);
  };
  return (
    <Box
      sx={{
        mt: { sm: 5, xs: 2.5 },
      }}
    >
      <Stack spacing={3}>
        <TextField fullWidth variant="outlined" id="mail" type="text" label="Email" />
        <TextField
          fullWidth
          variant="outlined"
          id="password"
          label="Password"
          type={showPassword ? 'text' : 'password'}
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
        <Button
          color="primary"
          variant="contained"
          size="large"
          fullWidth
          component={Link}
          href="#!"
          type="submit"
        >
          Sign Up
        </Button>
        <Stack
          spacing={0.5}
          sx={{
            textAlign: 'center',
            color: 'text.secondary',
            mb: 1,
          }}
        >
          <Typography variant="subtitle1">By creating account, you agree to our</Typography>
          <Link href="#!">
            <Typography color="primary" variant="subtitle1">
              Terms of Service
            </Typography>
          </Link>
        </Stack>
      </Stack>
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
          Or create an account using:
        </Typography>
        <Button
          startIcon={<IconifyIcon icon="flat-color-icons:google" />}
          sx={{ typography: { sm: 'button', xs: 'subtitle1', whiteSpace: 'nowrap' } }}
          variant="outlined"
        >
          Continue with Google
        </Button>
        <Button
          startIcon={<IconifyIcon icon="logos:facebook" />}
          sx={{ typography: { sm: 'button', xs: 'subtitle1', whiteSpace: 'nowrap' } }}
          variant="outlined"
        >
          Continue with Facebook
        </Button>
      </Stack>
    </Box>
  );
};

export default RegisterForm;
