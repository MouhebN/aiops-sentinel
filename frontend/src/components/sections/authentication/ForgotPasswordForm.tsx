import { Box, Button, Divider, Link, Stack, TextField, Typography } from '@mui/material';

const ForgotPasswordForm = () => {
  return (
    <Box
      sx={{
        mt: { sm: 5, xs: 2.5 },
      }}
    >
      <Stack spacing={3}>
        <TextField fullWidth variant="outlined" id="email" type="email" label="Email" />
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
        Send Password Reset Link
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

export default ForgotPasswordForm;
