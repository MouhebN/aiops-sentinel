/**
 * Formats a given amount as a currency using the specified options.
 * @param {number} amount - The amount to format as currency.
 * @param {Intl.NumberFormatOptions} [options={}] - Additional options for
 */
export const currencyFormat = (amount: number, options: Intl.NumberFormatOptions = {}) => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'usd',
    maximumFractionDigits: 2,
    minimumFractionDigits: 0,
    ...options,
  }).format(amount);
};

/** Generates an array of numbers within a specified range. */
export const getNumbersInRange = (startAt: number, endAt: number) => {
  return [...Array(endAt + 1 - startAt).keys()].map((i) => i + startAt);
};

/** formats a number according to the specified notation style,*/
export const numberFormat = (number: number, notation: 'standard' | 'compact' = 'standard') =>
  new Intl.NumberFormat('en-US', {
    notation,
  }).format(number);

/** Formats a number as a percentage with two decimal places. */
export const percentageFormat = (number: number) =>
  new Intl.NumberFormat('en-US', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(number / 100);
