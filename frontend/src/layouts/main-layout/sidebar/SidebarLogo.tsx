import { Link, Typography } from '@mui/material';
import Logo from 'components/icons/brand/Logo';

const SidebarLogo = () => {
  return (
    <Link
      href="/"
      sx={{
        height: 68,
        overflow: 'hidden',
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        // pl: theme.spacing(2.5),
      }}
    >
      <Logo fontSize={'large'} />
      <Typography variant="h4">AIOps Sentinel</Typography>
    </Link>
  );
};

export default SidebarLogo;
