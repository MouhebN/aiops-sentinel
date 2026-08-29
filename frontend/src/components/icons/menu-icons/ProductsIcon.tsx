import { SvgIcon, SvgIconProps } from '@mui/material';

const ProductsIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M12 2c5.523 0 10 4.477 10 10s-4.477 10-10 10S2 17.523 2 12 6.477 2 12 2zm0 2a8 8 0 100 16 8 8 0 000-16zm4 6a4 4 0 00-8 0h2l.005-.15A2 2 0 1112 12a1 1 0 00-1 1v1a1 1 0 102 0v-.126c1.725-.444 3-2.01 3-3.874zm-3 8v-2h-2v2h2z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default ProductsIcon;
