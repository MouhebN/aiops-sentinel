import { Backdrop, CircularProgress } from '@mui/material';

const Splash = () => {
  return (
    <Backdrop
      open={true}
      component="main"
      sx={{
        color: 'common.white',
        zIndex: (theme) => theme.zIndex.drawer + 1,
      }}
    >
      <CircularProgress color="inherit" />
    </Backdrop>
  );
};

export default Splash;
