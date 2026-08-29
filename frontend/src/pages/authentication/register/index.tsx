import { Box, Card, Grid, Link, Stack, Typography } from '@mui/material';
import RegisterForm from 'components/sections/authentication/RegisterForm';

const SignUpPage = () => {
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
                Create New Account
              </Typography>

              <Typography variant="button" color="text.secondary">
                Have an account?
                <Typography
                  ml={1}
                  color="primary"
                  component={Link}
                  href="/authentication/login"
                  variant="button"
                >
                  Sign In
                </Typography>
              </Typography>
            </Stack>
            <RegisterForm />
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default SignUpPage;
