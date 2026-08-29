import { Box, Container, Stack } from '@mui/material';
import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Footer from './footer/Footer';
import MainNavbar from './navbar/MainNavbar';
import Sidebar from './sidebar/Sidbar';

const drawerWidth = 270;

const MainLayout = () => {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [isClosing, setIsClosing] = useState(false);

  const handleDrawerClose = () => {
    setIsClosing(true);
    setMobileOpen(false);
  };

  const handleDrawerTransitionEnd = () => {
    setIsClosing(false);
  };

  const handleDrawerToggle = () => {
    if (!isClosing) {
      setMobileOpen(!mobileOpen);
    }
  };

  return (
    <Stack
      sx={{
        height: 1,
        position: 'relative',
        flexDirection: 'row',
        width: 1,
      }}
    >
      <Sidebar
        onDrawerClose={handleDrawerClose}
        onDrawerTransitionEnd={handleDrawerTransitionEnd}
        mobileOpen={mobileOpen}
      />

      <Stack
        spacing={2}
        sx={{
          display: 'flex',
          flexGrow: 1,
          width: 1,
          maxWidth: { xs: 1, lg: `calc(100% - ${drawerWidth}px)` },
          justifyContent: 'space-between',
        }}
      >
        <MainNavbar onDrawerToggle={handleDrawerToggle} />
        <Container>
          <Box>
            <Outlet />
          </Box>
        </Container>
        <Footer />
      </Stack>
    </Stack>
  );
};

export default MainLayout;
