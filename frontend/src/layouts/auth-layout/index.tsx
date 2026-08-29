import { Box, Container } from '@mui/material';
import { Outlet } from 'react-router-dom';

const AuthLayout = () => {
  return (
    <Box
      position="absolute"
      sx={{
        display: 'flex',
        minHeight: '100vh',
        bgcolor: 'background.default',
        placeItems: 'center',
        flexGrow: 1,
        width: 1,
        justifyContent: 'space-between',
      }}
    >
      <Container
        sx={{
          p: 0,
        }}
      >
        <Outlet />
      </Container>
    </Box>
  );
};

export default AuthLayout;
