import { Box, Card, Grid, Stack, Typography } from '@mui/material';
import ForgotPasswordForm from 'components/sections/authentication/ForgotPasswordForm';

const ForgotPasswordPage = () => {
  return (
    <Box mx="auto" sx={{ mx: 'auto', p: 4, width: 1, height: 1 }}>
      <Grid container spacing={1} justifyContent="center" alignItems="center">
        <Grid item xs={12} sm={9} md={6} lg={5} xl={4}>
          <Card
            sx={{
              py: { xs: 3, sm: 6 },
              px: { xs: 5, sm: 7.5 },

              bgcolor: 'common.white',
            }}
          >
            <Stack
              spacing={1}
              sx={{
                mb: 1,
                textAlign: 'center',
                gap: { xs: 1, sm: 0 },
              }}
            >
              <Typography
                variant="h1"
                sx={{
                  typography: {
                    whiteSpace: 'wrap',
                  },
                }}
              >
                Forgot your password ?
              </Typography>

              <Typography variant="button" color="text.secondary">
                We Will Help You Reset your Password
              </Typography>
            </Stack>
            <ForgotPasswordForm />
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ForgotPasswordPage;
