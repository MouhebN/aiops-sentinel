import { useAuth } from 'auth/AuthContext';
import { Box, List } from '@mui/material';
import { useLocation } from 'react-router-dom';
import Menuitems from './MenuItems';
import NavItemGroup from './NavItemGroup';
import NavMenuItem from './NavMenuItem';

const SidebarItems = () => {
  const { hasRole } = useAuth();
  const location = useLocation();
  const { pathname } = location;
  return (
    <Box sx={{ p: 2 }}>
      <List sx={{ pt: 0 }}>
        {Menuitems.map((item) => {
          if (item.roles && !hasRole(...item.roles)) {
            return null;
          }
          if (item.subheader) {
            return <NavItemGroup subheader={item.subheader} key={item.subheader} />;
          } else {
            return <NavMenuItem pathTo={pathname} item={item} key={item.id} />;
          }
        })}
      </List>
    </Box>
  );
};

export default SidebarItems;
