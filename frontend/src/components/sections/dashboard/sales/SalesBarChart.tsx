import { SxProps, useTheme } from '@mui/material';
import ReactEchart from 'components/base/ReactEchart';
import EChartsReactCore from 'echarts-for-react/lib/core';
import { BarChart, BarSeriesOption } from 'echarts/charts';
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
import { CanvasRenderer } from 'echarts/renderers';
import { tooltipFormatterList } from 'helpers/echart-utils';
import 'index.css';
import React, { useMemo } from 'react';

// Use ComposeOption to compose an Option type that only has required components and charts
export type ECOption = echarts.ComposeOption<
  | BarSeriesOption
  | TitleComponentOption
  | GridComponentOption
  | DatasetComponentOption
  | TooltipComponentOption
>;

// Register required components
echarts.use([BarChart, LegendComponent, CanvasRenderer, GridComponent]);

interface ReactEChartProps {
  seriesData: {
    title?: string;
    color?: string;
    orders: number[];
    date: string[];
  } | null;
  chartRef: React.MutableRefObject<EChartsReactCore | null>;
  sx?: SxProps;
}

const SalesBarChart = ({ seriesData, chartRef, ...rest }: ReactEChartProps) => {
  const theme = useTheme();
  const chartOptions: ECOption = useMemo(() => {
    const xAxisData = seriesData?.date;

    return {
      tooltip: {
        trigger: 'axis',
        backgroundColor: theme.palette.dark.main,
        textStyle: { color: theme.palette.common.white },
        formatter: tooltipFormatterList,
        extraCssText: 'box-shadow: none;border: none; padding: 0;', // override default tooltip style
        position: function (point, params, dom, rect, size) {
          const tooltipWidth = size.contentSize[0];
          let offsetY = 50;
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
        left: '-12%',
        top: '1%',
        right: '-5%',
        containLabel: true,
        bottom: '-1%',
      },
      xAxis: {
        type: 'category',
        data: xAxisData,
        nameGap: 0,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          margin: 14,
          fontSize: 12,
        },
      },
      yAxis: {
        type: 'value',
        show: false,
      },
      series: [
        {
          name: 'orders',
          data: seriesData?.orders,
          type: 'bar',
          barGap: '0%',
          emphasis: {
            itemStyle: {
              color: theme.palette.success.dark,
            },
          },
          itemStyle: {
            borderRadius: [40, 40, 0, 0],
            color: theme.palette.success.main,
          },
          barWidth: 8,
        },
      ],
    };
  }, [seriesData, theme]);

  return <ReactEchart echarts={echarts} option={chartOptions} ref={chartRef} {...rest} />;
};

export default SalesBarChart;
