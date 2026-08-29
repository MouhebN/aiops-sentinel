import { Theme } from '@mui/material';

import type { DataGridComponents } from '@mui/x-data-grid/themeAugmentation';
import typography from 'theme/typography';

const DataGridComponent: DataGridComponents<Omit<Theme, 'components'>>['MuiDataGrid'] = {
  defaultProps: {},
  styleOverrides: {
    root: ({ theme }) => ({
      borderRadius: theme.shape.borderRadius * 1.5,
      boxShadow: theme.shadows[1],
      backgroundColor: theme.palette.common.white,
      paddingTop: theme.spacing(2.5),
      paddingBottom: theme.spacing(0),
      '--DataGrid-containerBackground': theme.palette.common.white,
      '--DataGrid-rowBorderColor': theme.palette.action.selected,
      '& .MuiDataGrid-filler': {
        flex: 0,
      },
    }),
    overlayWrapper: {
      height: 'auto !important',
    },
    overlayWrapperInner: {
      height: 'auto !important',
    },
    main: ({ theme }) => ({
      borderTopRightRadius: theme.shape.borderRadius * 4,
      borderTopLeftRadius: theme.shape.borderRadius * 4,
    }),
    columnSeparator: {
      display: 'none',
    },
    filler: ({ theme }) => ({
      backgroundColor: theme.palette.common.white,
    }),
    row: ({ theme }) => ({
      '&:hover': {
        backgroundColor: theme.palette.background.default,
      },
    }),

    cell: ({ theme }) => ({
      ...typography.subtitle1,
      color: theme.palette.text.primary,
      borderColor: theme.palette.grey[200],
      padding: theme.spacing(1),
      paddingLeft: theme.spacing(3),
      paddingRight: theme.spacing(3),
      outline: 'none !important',
      display: 'flex',
      alignItems: 'center',
      '&:hover': {
        color: theme.palette.primary.main,
        cursor: 'pointer',
      },
      '&:focus-within': {
        outline: 'none !important',
      },
    }),

    columnHeader: ({ theme }) => ({
      ...typography.subtitle1,
      backgroundColor: theme.palette.common.white,
      paddingLeft: theme.spacing(3),
      paddingTop: theme.spacing(2),
      '&:focus-within': {
        outline: 'none !important',
      },
      '&::after': {
        outline: 'none !important',
      },
    }),

    columnHeaderTitle: ({ theme }) => ({
      fontWeight: typography.fontWeightRegular,
      color: theme.palette.text.secondary,
    }),
    columnHeaderTitleContainer: {
      justifyContent: 'start !important',
    },
    footerContainer: ({ theme }) => ({
      paddingTop: theme.spacing(1.5),
      paddingBottom: theme.spacing(2.5),
      border: 'none',
    }),
    sortIcon: ({ theme }) => ({
      width: theme.spacing(2),
      height: theme.spacing(2),
      color: theme.palette.primary.main,
    }),
    overlay: ({ theme }) => ({
      backgroundColor: theme.palette.action.selected,
      fontSize: theme.typography.subtitle1.fontSize,
      fontWeight: theme.typography.subtitle1.fontWeight,
      fontFamily: theme.typography.body1.fontFamily,
      flex: 1,
    }),
    virtualScroller: {
      display: 'flex',
      flexDirection: 'column',
      //   height: pxToRem(332), // to fix table height in data grid
    },
  },
};

export default DataGridComponent;
