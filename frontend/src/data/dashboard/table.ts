import product1 from 'assets/images/products/product-1.png';
import product2 from 'assets/images/products/product-2.png';
import product3 from 'assets/images/products/product-3.png';
import product4 from 'assets/images/products/product-4.png';
import product5 from 'assets/images/products/product-5.png';
import { uniqueId } from 'lodash';

export interface TransactionRowData {
  id: string;
  name: string;
  date: string;
  amount: number;
  status: 'pending' | 'paid';
}
export interface ItemType {
  title: string;
  image?: string;
}

export interface TopProductsRowData {
  id: string;
  product: ItemType;
  price: number;
  sold: number;
  searchableText?: string;
}

export const transactionTableData: TransactionRowData[] = [
  {
    id: uniqueId(),
    name: 'John Doe',
    date: '2024-01-04',
    amount: 100,
    status: 'pending',
  },
  {
    id: uniqueId(),
    name: 'Jane Smith',
    date: '2024-02-05',
    amount: 150,
    status: 'pending',
  },
  {
    id: uniqueId(),
    name: 'Alice Johnson',
    date: '2024-03-14',
    amount: 200,
    status: 'paid',
  },
  {
    id: uniqueId(),
    name: 'Bob Williams',
    date: '2024-03-04',
    amount: 120,
    status: 'pending',
  },
  {
    id: uniqueId(),
    name: 'Eva Brown',
    date: '2024-05-30',
    amount: 180,
    status: 'paid',
  },
  {
    id: uniqueId(),
    name: 'Michael Davis',
    date: '2024-05-01',
    amount: 250,
    status: 'pending',
  },
  {
    id: uniqueId(),
    name: 'Emily Wilson',
    date: '2024-04-03',
    amount: 300,
    status: 'paid',
  },
  {
    id: uniqueId(),
    name: 'David Taylor',
    date: '2024-04-13',
    amount: 130,
    status: 'pending',
  },
  {
    id: uniqueId(),
    name: 'Olivia Clark',
    date: '2024-04-23',
    amount: 180,
    status: 'paid',
  },
  {
    id: uniqueId(),
    name: 'William Martinez',
    date: '2024-04-24',
    amount: 220,
    status: 'pending',
  },
  {
    id: uniqueId(),
    name: 'Sophia Anderson',
    date: '2024-04-13',
    amount: 200,
    status: 'paid',
  },
  {
    id: uniqueId(),
    name: 'James Thompson',
    date: '2024-04-01',
    amount: 160,
    status: 'pending',
  },
  {
    id: uniqueId(),
    name: 'Emma Garcia',
    date: '2024-04-08',
    amount: 210,
    status: 'paid',
  },
  {
    id: uniqueId(),
    name: 'Alexander Hernandez',
    date: '2024-04-22',
    amount: 190,
    status: 'pending',
  },
  {
    id: uniqueId(),
    name: 'Mia Lopez',
    date: '2024-04-28',
    amount: 230,
    status: 'paid',
  },
];

export const topProductsTableData: TopProductsRowData[] = [
  {
    id: uniqueId(),
    product: { title: 'Men Grey Hoodie', image: product1 },
    price: 99.9,
    sold: 5,
  },
  {
    id: uniqueId(),
    product: { title: 'Women Striped T-Shirt', image: product2 },
    price: 54.9,
    sold: 5,
  },
  {
    id: uniqueId(),
    product: { title: 'Wome White T-Shirt', image: product3 },
    price: 24.7,
    sold: 5,
  },
  {
    id: uniqueId(),
    product: { title: 'Men White T-Shirt', image: product4 },
    price: 44.4,
    sold: 5,
  },
  {
    id: uniqueId(),
    product: { title: 'Men White T-Shirt', image: product5 },
    price: 34.9,
    sold: 5,
  },
];
