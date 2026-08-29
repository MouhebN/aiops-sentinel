const echart = () => ({
  '.echarts-for-react': {
    overflow: 'hidden',
    height: '100% !important',

    '&:not(&.echart-map)': {
      '> div': {
        '&:first-of-type': {
          height: '100% !important',
        },
      },
    },
  },
});

export default echart;
