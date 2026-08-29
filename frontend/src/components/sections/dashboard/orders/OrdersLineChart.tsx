// OrdersLineChart.tsx
import ReactEchart from 'components/base/ReactEchart';
import EChartsReactCore from 'echarts-for-react/lib/core';
import { LineChart, LineSeriesOption } from 'echarts/charts';
import {
  DatasetComponentOption,
  GridComponent,
  GridComponentOption,
  LegendComponent,
  TitleComponentOption,
  TooltipComponentOption,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import 'echarts/lib/component/tooltip';
import 'index.css';
import React, { useMemo } from 'react';

// Use ComposeOption to compose an Option type that only has required components and charts
export type ECOption = echarts.ComposeOption<
  | LineSeriesOption
  | TitleComponentOption
  | GridComponentOption
  | DatasetComponentOption
  | TooltipComponentOption
>;

// Register required components
echarts.use([LegendComponent, CanvasRenderer, GridComponent, LineChart]);

interface ReactEChartProps {
  seriesData: { today: number[]; yesterday: number[]; time: string[] };
  chartRef: React.MutableRefObject<EChartsReactCore | null>;
  sx?: SxProps;
}
// chartOptions.ts
import { SxProps, useTheme } from '@mui/material';
import dayjs from 'dayjs';
import { CanvasRenderer } from 'echarts/renderers';
import { tooltipFormatterList } from 'helpers/echart-utils';

const OrdersLineChart = ({ seriesData, chartRef, ...rest }: ReactEChartProps) => {
  const theme = useTheme();

  const chartOptions: ECOption = useMemo(() => {
    const xAxisData = seriesData.time;
    const todayLabel = dayjs().format('MMM DD');
    const yesterdayLabel = dayjs().subtract(1, 'day').format('MMM DD');

    return {
      tooltip: {
        trigger: 'axis',
        backgroundColor: theme.palette.dark.main,
        textStyle: { color: theme.palette.common.white },
        formatter: tooltipFormatterList,
        extraCssText: 'box-shadow: none;border: none;', // override default tooltip style
        padding: 0,
        position: function (point, params, dom, rect, size) {
          const tooltipWidth = size.contentSize[0];
          let offsetY = 80;
          if (rect !== null) {
            offsetY += rect?.height ?? 0;
          }
          return [point[0] - tooltipWidth / 2, point[1] - offsetY];
        },
      },
      legend: {
        show: false,
      },
      grid: {
        left: '1%',
        right: '1%',
        bottom: '4%',
        top: '12%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        boundaryGap: true,
        data: xAxisData,
        axisTick: {
          show: false,
        },
        axisLine: {
          onZero: false,
          show: false,
        },
        axisLabel: {
          color: '#a1a7c3',
          fontSize: '12px',
          margin: 30,
        },
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          color: '#a1a7c3',
          fontSize: '12px',
          margin: 17,
        },
        splitLine: {
          lineStyle: {
            type: 'dashed',
            dashOffset: 55,
            miterLimit: 10,
            width: 1.1,
            join: 'bevel',
            cap: 'butt',
          },
        },
      },
      series: [
        {
          name: todayLabel,
          type: 'line',
          data: seriesData.today,
          color: '#2968fa',
          emphasis: {
            focus: 'none',
            scale: 4,
            itemStyle: {
              borderCap: 'round',
              borderType: 'solid',
              borderWidth: 4,
            },
          },
          showSymbol: false,
          lineStyle: {
            width: 4,
          },
          z: 2,
        },
        {
          name: yesterdayLabel,
          type: 'line',
          data: seriesData.yesterday,
          color: '#d9e1ec',
          emphasis: {
            focus: 'none',
            scale: 4,
            itemStyle: {
              borderCap: 'round',
              borderType: 'solid',
              borderWidth: 4,
            },
          },
          showSymbol: false,
          lineStyle: {
            width: 4,
          },
          z: 1,
        },
      ],
    };
  }, [seriesData, theme]);

  return <ReactEchart echarts={echarts} option={chartOptions} ref={chartRef} {...rest} />;
};

export default OrdersLineChart;
