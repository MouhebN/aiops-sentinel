import { TypographyOptions } from '@mui/material/styles/createTypography';
import pxToRem from './functions/pxToRem';

declare module '@mui/material/styles/createTypography' {
  interface TypographyOptions {
    fontWeightLighter?: number;
  }
  interface TypographyOptions {
    fontWeightExtraBold?: number;
  }

  interface Typography {
    fontWeightExtraBold: number;
  }
}

const typography: TypographyOptions = {
  fontFamily: ['Inter', 'Open Sans', 'sans-serif'].join(','),
  fontSize: 14,
  fontWeightLighter: 100,
  fontWeightLight: 300,
  fontWeightRegular: 400,
  fontWeightMedium: 600,
  fontWeightBold: 700,
  fontWeightExtraBold: 800,
  h1: {
    fontSize: pxToRem(32),
    lineHeight: 1.375,
    fontWeight: 700,
  },

  h2: {
    fontWeight: 600,
    fontSize: pxToRem(24),
    lineHeight: 2.25,
  },

  h3: {
    fontSize: pxToRem(24),
    lineHeight: 1.5,
    fontWeight: 700,
  },

  h4: {
    fontSize: pxToRem(22),
    lineHeight: 1.5,
    fontWeight: 700,
  },

  h5: {
    fontSize: pxToRem(20),
    lineHeight: 1.75,
    fontWeight: 700,
  },

  h6: {
    fontSize: pxToRem(16),
    lineHeight: 1.5,
    fontWeight: 700,
  },

  subtitle1: {
    fontSize: pxToRem(14),
    lineHeight: 1.4286,
    fontWeight: 400,
  },

  subtitle2: {
    fontSize: pxToRem(12),
    fontWeight: 400,
    lineHeight: 1,
  },
  caption: {
    fontWeight: 700,
    fontSize: pxToRem(12),
    lineHeight: 1,
  },

  body1: {
    fontSize: pxToRem(14),
    fontWeight: 400,
    lineHeight: 1.5,
  },

  body2: {
    fontSize: pxToRem(12),
    letterSpacing: '0rem',
    fontWeight: 400,
    lineHeight: 1.333,
  },

  button: {
    fontSize: pxToRem(16),
    fontWeight: 400,
    lineHeight: 1.5,
    textTransform: 'none',
  },
};

export default typography;
// 16 = 1 (1.142857)
// 20 = 1.25 (1.42857)
// 24 = 1.5 (1.7142857)
// 32 = 2.75 (2.2857)
