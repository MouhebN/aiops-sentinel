import { Breakpoint, Theme, useMediaQuery } from '@mui/material';
import { createContext, PropsWithChildren, useContext, useEffect, useState } from 'react';

interface BreakpointContextInterface {
  currentBreakpoint: Breakpoint;
  up: (key: Breakpoint | number) => boolean;
  down: (key: Breakpoint | number) => boolean;
  only: (key: Breakpoint | number) => boolean;
  between: (start: Breakpoint | number, end: Breakpoint | number) => boolean;
}

export const BreakpointContext = createContext({} as BreakpointContextInterface);

/**
 * A provider component that manages the current breakpoint based on the screen size.
 * @param {PropsWithChildren} children - The child components to be wrapped by the provider.
 * @returns JSX element that provides the current breakpoint and media query functions.
 */
const BreakpointsProvider = ({ children }: PropsWithChildren) => {
  const [currentBreakpoint, setCurrentBreakpoint] = useState<Breakpoint>('xs');
  const up = (key: Breakpoint | number) =>
    useMediaQuery<Theme>((theme) => theme.breakpoints.up(key));

  const down = (key: Breakpoint | number) =>
    useMediaQuery<Theme>((theme) => theme.breakpoints.down(key));

  const only = (key: Breakpoint | number) =>
    useMediaQuery<Theme>((theme) => theme.breakpoints.only(key as Breakpoint));

  const between = (start: Breakpoint | number, end: Breakpoint | number) =>
    useMediaQuery<Theme>((theme) => theme.breakpoints.between(start, end));

  const isXs = between('xs', 'sm');
  const isSm = between('sm', 'md');
  const isMd = between('md', 'lg');
  const isLg = between('lg', 'xl');
  const isXl = up('xl');

  useEffect(() => {
    if (isXs) {
      setCurrentBreakpoint('xs');
    }
    if (isSm) {
      setCurrentBreakpoint('sm');
    }
    if (isMd) {
      setCurrentBreakpoint('md');
    }
    if (isLg) {
      setCurrentBreakpoint('lg');
    }
    if (isXl) {
      setCurrentBreakpoint('xl');
    }
  }, [isXs, isSm, isMd, isLg, isXl]);

  return (
    <BreakpointContext.Provider value={{ currentBreakpoint, up, down, only, between }}>
      {children}
    </BreakpointContext.Provider>
  );
};

export const useBreakpoints = () => useContext(BreakpointContext);

export default BreakpointsProvider;
