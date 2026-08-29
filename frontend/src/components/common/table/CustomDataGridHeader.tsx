import { IconButton, Stack, TextField, Typography } from '@mui/material';
import { GridSlotsComponentsProps } from '@mui/x-data-grid';
import IconifyIcon from 'components/base/IconifyIcon';

const CustomDataGridHeader = (props: NonNullable<GridSlotsComponentsProps['toolbar']>) => {
  const handleChange = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    if (props.onChange) {
      props.onChange(event as React.ChangeEvent<HTMLInputElement>);
    }
  };

  return (
    <Stack
      sx={{
        my: 1,
        alignItems: 'center',
        justifyContent: 'space-between',
        flexDirection: { xs: 'column', sm: 'row' },
        gap: { xs: 2, sm: 5.5 },
      }}
    >
      <Typography variant="h6" sx={{ flex: 1, typography: { whiteSpace: 'nowrap' } }}>
        {props.title}
      </Typography>

      <TextField
        type="text"
        size="small"
        color="secondary"
        variant="filled"
        value={props.value}
        onChange={handleChange}
        placeholder={`Search ${props.flag}...`}
        InputProps={{
          endAdornment: (
            <IconButton
              title="Clear"
              aria-label="Clear"
              size="small"
              style={{ visibility: props.value ? 'visible' : 'hidden' }}
              onClick={props.clearSearch}
            >
              <IconifyIcon icon="mdi:clear-circle" fontSize="1rem" />
            </IconButton>
          ),
        }}
      />
    </Stack>
  );
};

export default CustomDataGridHeader;
