import {
  Alert,
  Box,
  Button,
  Checkbox,
  Divider,
  FormControlLabel,
  FormGroup,
  IconButton,
  InputAdornment,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useAuth } from 'auth/AuthContext';
import IconifyIcon from 'components/base/IconifyIcon';
import { FormEvent, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import paths from 'routes/path';

const LoginForm = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [email, setEmail] = useState('admin@aiops.local');
  const [password, setPassword] = useState('admin123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const handleClickShowPassword = () => {
    setShowPassword((prevShowPassword) => !prevShowPassword);
  };
  const redirectTo = (location.state as { from?: { pathname?: string } } | undefined)?.from
    ?.pathname;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    try {
      setLoading(true);
      setError(null);
      await login(email, password);
      navigate(redirectTo || paths.default, { replace: true });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Login failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      component="form"
      sx={{
        mt: { sm: 5, xs: 2.5 },
      }}
      onSubmit={handleSubmit}
    >
      <Stack spacing={3}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField
          fullWidth
          variant="outlined"
          id="mail"
          type="email"
          label="Email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <TextField
          fullWidth
          variant="outlined"
          id="password"
          type={showPassword ? 'text' : 'password'}
          label="Password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
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
      </Stack>
      <FormGroup sx={{ my: 2 }}>
        <FormControlLabel
          control={<Checkbox />}
          label="Keep me signed in"
          sx={{
            color: 'text.secondary',
          }}
        />
      </FormGroup>
      <Button
        color="primary"
        variant="contained"
        size="large"
        fullWidth
        type="submit"
        disabled={loading}
      >
        {loading ? 'Signing In...' : 'Sign In'}
      </Button>
      <Stack
        sx={{
          textAlign: 'center',
          color: 'text.secondary',
          my: 3,
        }}
      >
        <Link href="/authentication/forgot-password">
          <Typography color="primary" variant="subtitle1">
            Forgot Your Password?
          </Typography>
        </Link>
      </Stack>
      <Divider sx={{ my: 3 }} />
      <Typography textAlign="center" color="text.secondary" variant="body2">
        Test accounts: admin, operator, viewer
      </Typography>
    </Box>
  );
};

export default LoginForm;
