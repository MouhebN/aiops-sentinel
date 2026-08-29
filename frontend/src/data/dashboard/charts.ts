type TimeOrders = { time: string; orders: number }[];
type SalesRevenue = {
  day: string;
  orders: number;
}[];
export interface ComparisonChartData {
  today: TimeOrders;
  yesterday: TimeOrders;
}
export interface SalesChartData {
  revenue: SalesRevenue;
}
export const ordersOverTimeData: ComparisonChartData = {
  today: [
    { time: '12am', orders: 20 },
    { time: '1am', orders: 32 },
    { time: '2am', orders: 46 },
    { time: '3am', orders: 54 },
    { time: '4am', orders: 66 },
    { time: '5am', orders: 76 },
    { time: '6am', orders: 17 },
    { time: '7am', orders: 18 },
    { time: '8am', orders: 11 },
    { time: '9am', orders: 13 },
    { time: '10am', orders: 8 },
    { time: '11am', orders: 66 },
    { time: '12pm', orders: 56 },
    { time: '1pm', orders: 15 },
    { time: '2pm', orders: 9 },
  ],
  yesterday: [
    { time: '12am', orders: 10 },
    { time: '1am', orders: 15 },
    { time: '2am', orders: 95 },
    { time: '3am', orders: 95 },
    { time: '4am', orders: 98 },
    { time: '5am', orders: 14 },
    { time: '6am', orders: 73 },
    { time: '7am', orders: 65 },
    { time: '8am', orders: 85 },
    { time: '9am', orders: 16 },
    { time: '10am', orders: 25 },
    { time: '11am', orders: 14 },
    { time: '12pm', orders: 14 },
    { time: '1pm', orders: 16 },
    { time: '2pm', orders: 16 },
  ],
};

export const salesData: SalesChartData = {
  revenue: [
    { day: '12', orders: 2000 },
    { day: '13', orders: 1200 },
    { day: '14', orders: 1600 },
    { day: '15', orders: 1400 },
    { day: '16', orders: 2600 },
    { day: '17', orders: 1600 },
    { day: '18', orders: 1700 },
  ],
};
