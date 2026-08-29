import { Box, Drawer } from '@mui/material';
import { useBreakpoints } from 'providers/useBreakPoint';
import SimpleBar from 'simplebar-react';
import SidebarItems from './SidebarItems';
import SidebarLogo from './SidebarLogo';
interface SideNavProps {
  onDrawerClose: () => void;
  onDrawerTransitionEnd: () => void;
  mobileOpen: boolean;
}
const Sidebar = ({ onDrawerClose, onDrawerTransitionEnd, mobileOpen }: SideNavProps) => {
  const { up } = useBreakpoints();
  const upLg = up('lg');

  if (upLg) {
    return (
      <Box
        sx={{
          flexShrink: 0,
        }}
      >
        <Drawer
          anchor="left"
          open
          variant="permanent"
          sx={{
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
            },
          }}
        >
          <Box
            sx={{
              height: 1,
            }}
          >
            <Box
              sx={{
                bgcolor: 'common.white',
                px: 3,
                boxShadow: 9,
              }}
            >
              <SidebarLogo />
            </Box>

            <SimpleBar style={{ height: 'calc(100% - 68px)' }}>
              <Box
                sx={{
                  borderRight: 1,
                  borderColor: 'text.disabled',
                }}
              >
                <SidebarItems />
              </Box>
            </SimpleBar>
          </Box>
        </Drawer>
      </Box>
    );
  }
  {
    /* Sidebar For Mobile */
  }

  return (
    <Drawer
      anchor="left"
      onTransitionEnd={onDrawerTransitionEnd}
      open={mobileOpen}
      onClose={onDrawerClose}
      variant="temporary"
      ModalProps={{
        keepMounted: true, // Better open performance on mobile.
      }}
      PaperProps={{
        sx: {
          backgroundColor: 'common.white',
          border: '0 !important',
          boxShadow: (theme) => theme.shadows[2],
        },
      }}
    >
      {/* ------------------------------------------- */}
      <Box
        sx={{
          bgcolor: 'common.white',
          px: 3,
        }}
      >
        <SidebarLogo />
      </Box>

      {/* ------------------------------------------- */}
      <SimpleBar style={{ height: 'calc(100% - 68px)' }}>
        <SidebarItems />
      </SimpleBar>
    </Drawer>
  );
};

export default Sidebar;
