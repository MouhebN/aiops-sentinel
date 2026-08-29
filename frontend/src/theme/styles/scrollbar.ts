import { Theme } from '@mui/material';

const scrollbar = (theme: Theme) => ({
  '@supports (-moz-appearance:none)': {
    scrollbarColor: `${theme.palette.grey[300]} transparent`,
  },
  '*::-webkit-scrollbar': {
    visibility: 'hidden',
    WebkitAppearance: 'none',
    width: 0,
    height: 0,
    backgroundColor: 'transparent',
  },

  '*::-webkit-scrollbar-thumb': {
    visibility: 'hidden',
    borderRadius: theme.shape.borderRadius * 0.75,
    backgroundColor: theme.palette.grey[300],
  },
});
export default scrollbar;
