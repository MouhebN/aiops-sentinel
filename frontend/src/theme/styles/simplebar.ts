import { Theme } from '@mui/material';

const simplebar = (theme: Theme) => ({
  '& .simplebar-track': {
    '&.simplebar-vertical': {
      '& .simplebar-scrollbar': {
        '&:before': {
          cursor: 'grab',
          border: 1,
          borderStyle: 'solid',
          borderColor: theme.palette.common.white,
          maxHeight: '100vh',
          background: `${theme.palette.grey[300]}`,
          '&:hover': {
            backgroundColor: theme.palette.grey[800],
          },
        },

        '&.simplebar-visible': {
          '&:before': {
            opacity: 1,
            padding: 0,
          },
        },
      },
    },
  },
  '& .simplebar-wrapper': {
    '& .simplebar-content': {
      overflow: 'hidden',
    },
  },
});
export default simplebar;
