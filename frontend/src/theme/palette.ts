import { PaletteColorOptions, PaletteOptions } from '@mui/material';
import { blue, green, grey, orange, purple, red, silver, turquoise } from './colors';

declare module '@mui/material/styles' {
  interface PaletteOptions {
    neutral?: PaletteColorOptions;
    white?: PaletteColorOptions;
    dark?: PaletteColorOptions;
    transparent?: PaletteColorOptions;
    black?: PaletteColorOptions;
  }

  interface SimplePaletteColorOptions {
    lighter?: string;
    darker?: string;
    state?: string;
  }
  interface Palette {
    neutral: PaletteColor;
    dark: PaletteColor;
    transparent: PaletteColor;
  }

  interface PaletteColor {
    lighter: string;
    darker: string;
    state: string;
  }
}

const palette: PaletteOptions = {
  action: {
    active: grey[500],
    hover: grey[100],
    selected: grey[200],
    disabled: 'white',
    disabledBackground: grey[600],
    focus: grey[400],
  },
  background: {
    paper: grey[50],
    default: grey[100],
  },
  text: {
    primary: grey[900],
    secondary: grey[700],
    disabled: grey[300],
  },

  dark: {
    main: grey[800],
  },
  transparent: {
    main: 'transparent',
  },
  neutral: {
    main: silver[500],
  },
  primary: {
    main: blue[800],
    light: blue[200],
    lighter: blue[100],
    dark: blue[600],
  },
  secondary: {
    lighter: purple[50],
    light: purple[300],
    main: purple[500],
    dark: purple[700],
    darker: purple[900],
  },
  error: {
    lighter: red[50],
    light: red[100],
    main: red[600],
    dark: red[500],
    darker: red[900],
  },
  warning: {
    lighter: orange[50],
    light: orange[100],
    main: orange[500],
    dark: orange[700],
    darker: orange[900],
    contrastText: '#ffffff',
  },
  success: {
    lighter: green[50],
    light: green[100],
    main: green[600],
    dark: green[700],
    darker: green[900],
  },
  info: {
    lighter: turquoise[50],
    light: turquoise[300],
    main: turquoise[500],
    dark: turquoise[700],
    darker: turquoise[900],
    contrastText: '#ffffff',
  },

  grey: { ...grey },
};

export default palette;
