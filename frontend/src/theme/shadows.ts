const defaultShadows = [
  'none',
  `0rem 0rem 1rem 0rem rgba(23, 43, 77, 0.075)`,
  `0rem 0rem 1rem 0rem rgba(103, 116, 142, 0.075)`,
  '0px 1px 1px 0px rgba(0, 0, 0, 0.03), 2px 2px 10px 0px rgba(0, 0, 0, 0.09)',
  `0rem 0.25rem 0.375rem -0.0625rem rgba(20, 20, 20, 0.12), 0rem 0.125rem 0.25rem -0.0625rem rgba(20, 20, 20, 0.07)`,
  '0px 0.286px 1.134px 0px rgba(0, 0, 0, 0.02), 0px 1.36px 2.867px 0px rgba(0, 0, 0, 0.03), 1px 3.92px 5.79px 0px rgba(0, 0, 0, 0.04), 2px 9px 11px 0px rgba(0, 0, 0, 0.04)',
  '0px -2.46px 3.86px 0px rgba(0, 0, 0, 0.02), 0px 2.258px 4.692px 0px rgba(0, 0, 0, 0.02), 0px 6.147px 9.475px 0px rgba(0, 0, 0, 0.03), 4px 18px 18px 0px rgba(0, 0, 0, 0.04)',
  '0px -1px 3.15px 0px rgba(0, 0, 0, 0.02), 0px 5.695px 3.531px 0px rgba(0, 0, 0, 0.01), 1px 10.271px 9.478px 0px rgba(0, 0, 0, 0.03), 3px 24px 42px 0px rgba(0, 0, 0, 0.07)',
  'rgba(0, 0, 0, 0.2) 0px 5px 5px -3px, rgba(0, 0, 0, 0.14) 0px 8px 10px 1px, rgba(0, 0, 0, 0.12) 0px 3px 14px 2px;',
  '0px 1px 4px 0px rgba(21, 34, 50, 0.08);',
];

const fillShadows = (shadows: string[], totalShadows: number) => {
  const filledShadows = [...shadows];
  const defaultShadow = '0px 0px 0px 0px rgba(0, 0, 0, 0.0)';
  // Default shadow for filling

  while (filledShadows.length < totalShadows) {
    filledShadows.push(defaultShadow);
  }

  return filledShadows.slice(0, totalShadows);
};

const shadows = fillShadows(defaultShadows, 25);
export default shadows;
