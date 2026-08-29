import { Box, Grid, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import PageHeader from 'components/common/PageHeader';
import ApiState from 'components/aiops/ApiState';
import FilterCard from 'components/aiops/FilterCard';
import IconActionButton from 'components/aiops/IconActionButton';
import StatusChip from 'components/aiops/StatusChip';
import { formatDeviceType } from 'helpers/aiops';
import { useAiopsData } from 'hooks/useAiopsData';
import { Device, DEVICE_STATUSES, DEVICE_TYPES, DeviceStatus, DeviceType } from 'types/aiops';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';

const columns: GridColDef<Device>[] = [
  { field: 'name', headerName: 'Device', flex: 1.2, minWidth: 190 },
  {
    field: 'type',
    headerName: 'Type',
    flex: 0.8,
    minWidth: 130,
    valueGetter: (value) => formatDeviceType(value as string),
  },
  { field: 'location', headerName: 'Location', flex: 1, minWidth: 150 },
  {
    field: 'status',
    headerName: 'Status',
    flex: 0.7,
    minWidth: 130,
    renderCell: (params) => <StatusChip status={params.row.status} />,
  },
  {
    field: 'lastSeenAt',
    headerName: 'Last Seen',
    flex: 1,
    minWidth: 170,
    valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm:ss'),
  },
];

const DevicesPage = () => {
  const { devices, loading, error, refresh } = useAiopsData();
  const [searchText, setSearchText] = useState('');
  const [status, setStatus] = useState<DeviceStatus | ''>('');
  const [type, setType] = useState<DeviceType | ''>('');

  const filteredDevices = useMemo(
    () =>
      devices.filter((device) => {
        const searchValue = searchText.trim().toLowerCase();
        const matchesSearch =
          !searchValue ||
          [device.name, device.id, device.location, device.type, device.status]
            .join(' ')
            .toLowerCase()
            .includes(searchValue);
        const matchesStatus = !status || device.status === status;
        const matchesType = !type || device.type === type;

        return matchesSearch && matchesStatus && matchesType;
      }),
    [devices, searchText, status, type],
  );

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={<IconActionButton icon="mdi:refresh" label="Refresh devices" onClick={refresh} />}
      >
        Devices
      </PageHeader>
      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />
        <FilterCard title="Inventory Filters">
          <Grid container spacing={2} alignItems="flex-start">
            <Grid item xs={12} md={5}>
              <TextField
                fullWidth
                label="Search"
                value={searchText}
                onChange={(event) => setSearchText(event.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                select
                fullWidth
                label="Status"
                value={status}
                onChange={(event) => setStatus(event.target.value as DeviceStatus | '')}
              >
                <MenuItem value="">All statuses</MenuItem>
                {DEVICE_STATUSES.map((deviceStatus) => (
                  <MenuItem key={deviceStatus} value={deviceStatus}>
                    {deviceStatus}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                select
                fullWidth
                label="Device Type"
                value={type}
                onChange={(event) => setType(event.target.value as DeviceType | '')}
              >
                <MenuItem value="">All types</MenuItem>
                {DEVICE_TYPES.map((deviceType) => (
                  <MenuItem key={deviceType} value={deviceType}>
                    {formatDeviceType(deviceType)}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} md={1}>
              <Stack alignItems={{ xs: 'flex-start', md: 'center' }}>
                <IconActionButton
                  icon="mdi:filter-remove-outline"
                  label="Clear filters"
                  onClick={() => {
                    setSearchText('');
                    setStatus('');
                    setType('');
                  }}
                />
              </Stack>
            </Grid>
          </Grid>
        </FilterCard>
        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Infrastructure Inventory
          </Typography>
          <DataGrid
            rows={filteredDevices}
            columns={columns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            pageSizeOptions={[5, 10, 25]}
            initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          />
        </Paper>
      </Stack>
    </Box>
  );
};

export default DevicesPage;
