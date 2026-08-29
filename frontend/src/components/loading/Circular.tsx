import { CircularProgress, circularProgressClasses, Stack } from '@mui/material';

const CircularLoader = () => {
  return (
    <Stack
      direction="row"
      sx={{
        position: 'relative',
        w: 1,
        h: 1,
      }}
    >
      <CircularProgress
        variant="determinate"
        sx={{
          color: 'grey.200',
        }}
        size={40}
        thickness={4}
        value={100}
      />
      <CircularProgress
        variant="indeterminate"
        disableShrink
        sx={{
          color: 'primary.main',
          animationDuration: '550ms',
          position: 'absolute',
          left: 0,
          [`& .${circularProgressClasses.circle}`]: {
            strokeLinecap: 'round',
          },
        }}
        size={40}
        thickness={4}
      />
    </Stack>
  );
};

export default CircularLoader;
