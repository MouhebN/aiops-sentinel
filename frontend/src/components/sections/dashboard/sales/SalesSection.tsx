import { Card, CardContent, Divider, Stack, Typography } from '@mui/material';
import CircularLoader from 'components/loading/Circular';
import SalesBarChart from 'components/sections/dashboard/sales/SalesBarChart';
import { salesData } from 'data/dashboard/charts';
import EChartsReactCore from 'echarts-for-react/lib/core';
import { useEffect, useRef, useState } from 'react';

const SalesSection = () => {
  const chartRef = useRef<EChartsReactCore | null>(null);

  const [chartData, setChartData] = useState<{
    title?: string;
    color?: string;
    orders: number[];
    date: string[];
  } | null>(null);

  // Fetch sales data
  useEffect(() => {
    const fetchData = () => {
      const ordersData = salesData.revenue.map((item) => item.orders);
      const dateData = salesData.revenue.map((item) => item.day);
      setChartData({ orders: ordersData, date: dateData });
    };

    fetchData();
  }, []);

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
      <CardContent sx={{ p: 3.5, flex: 1, h: 1 }}>
        <Typography variant="h6">Last 7 Days Sales</Typography>
        <Stack spacing={2.5} sx={{ pt: 3 }}>
          <div>
            <Typography variant="h5">1,259</Typography>
            <Typography variant="subtitle1" color="text.secondary">
              Item Sold
            </Typography>
          </div>
          <div>
            <Typography variant="h5">$12,546</Typography>
            <Typography variant="subtitle1" color="text.secondary">
              Revenue
            </Typography>
          </div>
          <Divider />
        </Stack>
        <Stack
          direction="row"
          sx={{ height: 238, display: 'flex', justifyContent: 'center', width: 1 }}
        >
          {chartData ? (
            <SalesBarChart
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

export default SalesSection;
