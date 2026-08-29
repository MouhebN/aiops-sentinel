import {
  Button,
  Card,
  CardContent,
  FormControl,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import IconifyIcon from 'components/base/IconifyIcon';
import CircularLoader from 'components/loading/Circular';
import OrdersLineChart from 'components/sections/dashboard/orders/OrdersLineChart';
import { ordersOverTimeData } from 'data/dashboard/charts';
import dayjs from 'dayjs';
import EChartsReactCore from 'echarts-for-react/lib/core';
import { useEffect, useRef, useState } from 'react';

/**
 * Functional component that renders a line chart section with dynamic data.
 * @returns JSX element representing the line chart section.
 */
const OrdersSection = () => {
  const chartRef = useRef<EChartsReactCore | null>(null);
  const [chartData, setChartData] = useState<null | {
    today: number[];
    yesterday: number[];
    time: string[];
  }>(null);

  const todayLabel = dayjs().format('MMM DD');
  const yesterdayLabel = dayjs().subtract(1, 'day').format('MMM DD');

  // Simulating data fetching and processing
  useEffect(() => {
    const fetchData = async () => {
      const data = ordersOverTimeData;
      setChartData({
        today: data.today.map((item) => item.orders),
        yesterday: data.yesterday.map((item) => item.orders),
        time: data.today.map((item) => item.time),
      });
    };

    fetchData();
  }, []);

  const handleLegendToggle = (name: string) => {
    if (chartRef.current) {
      const instance = chartRef.current.getEchartsInstance();
      instance.dispatchAction({
        type: 'legendToggleSelect',
        name: name,
      });
    }
  };

  /**
   * Adds an event listener for window resize to handle manual chart resizing using Echarts.
   * It resizes the chart instance when the window is resized.
   */
  useEffect(() => {
    const handleResize = () => {
      if (chartRef.current) {
        const instance = chartRef.current.getEchartsInstance();
        instance.resize();
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <Card sx={{ height: 1 }}>
      <CardContent sx={{ flex: 1, p: 2 }}>
        <Stack
          direction="row"
          sx={{
            px: 1.5,
            pt: 1.5,
            justifyContent: 'space-between',
            alignItems: 'center',
            mb: 3,
            gap: 1,
          }}
        >
          <Typography variant="h6">Orders Over Time</Typography>
          <Stack direction="row" alignItems="center">
            <FormControl sx={{ minWidth: 80 }}>
              <Select
                variant="standard"
                labelId="demo-select-small-label"
                id="demo-simple-select"
                value={1}
                label="Age"
                size="medium"
                sx={{
                  mt: 1,
                  minWidth: 80,
                  color: 'text.secondary',
                  alignItems: 'center',
                }}
                // onChange={handleChange}
              >
                <MenuItem value={1} selected>
                  Last 12 hours
                </MenuItem>
                <MenuItem value={2}>Last 24 hours</MenuItem>
                <MenuItem value={3}>Last 48 hours</MenuItem>
              </Select>
            </FormControl>
          </Stack>
        </Stack>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'flex-end', mt: 2, px: 1.5 }}
        >
          <Stack
            spacing={{ xs: 0 }}
            sx={{
              gap: { xs: 2, sm: 4 },
              flexDirection: { xs: 'column', sm: 'row' },
              alignItems: { xs: 'flex-end', sm: 'center' },
            }}
          >
            <div>
              <Typography variant="h5">645</Typography>
              <Typography variant="subtitle1" color="text.secondary">
                Orders on May 22
              </Typography>
            </div>
            <div>
              <Typography variant="h5">645</Typography>
              <Typography variant="subtitle1" color="text.secondary">
                Orders on May 21
              </Typography>
            </div>
          </Stack>
          {chartData ? (
            <Stack
              direction="row"
              sx={{
                justifyContent: 'center',
                mt: { lg: 2 },
                flexDirection: { xs: 'column', lg: 'row' },
                spacing: { xs: 0, lg: 2.5 },
              }}
            >
              <Button
                size="small"
                startIcon={<IconifyIcon icon="ic:round-square" color="grey.400" />}
                key={yesterdayLabel}
                variant="text"
                sx={{ color: 'text.secondary', py: 0.5, px: 1 }}
                onClick={() => handleLegendToggle(yesterdayLabel)}
              >
                {yesterdayLabel}
              </Button>
              <Button
                size="small"
                startIcon={<IconifyIcon icon="ic:round-square" color="primary.main" />}
                key={todayLabel}
                variant="text"
                sx={{ color: 'text.secondary', py: 0.5, px: 1 }}
                onClick={() => handleLegendToggle(todayLabel)}
              >
                {todayLabel}
              </Button>
            </Stack>
          ) : (
            <Typography
              variant="body2"
              sx={{
                mr: 5,
                color: 'text.secondary',
              }}
            >
              Loading...
            </Typography>
          )}
        </Stack>
        <Stack
          direction="row"
          sx={{
            height: 300,
            display: 'flex',
            mt: 3,
            placeItems: 'center',
            justifyContent: 'center',
          }}
        >
          {chartData ? (
            <OrdersLineChart
              seriesData={chartData}
              chartRef={chartRef}
              sx={{
                flex: 1,
                display: 'flex',
                alignItems: 'end',
                overflow: 'visible',
              }}
            />
          ) : (
            <CircularLoader />
          )}
        </Stack>
      </CardContent>
    </Card>
  );
};

export default OrdersSection;
