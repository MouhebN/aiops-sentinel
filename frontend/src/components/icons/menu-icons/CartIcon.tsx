import { SvgIcon, SvgIconProps } from '@mui/material';

const CartIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M7.5 19a1.5 1.5 0 110 3 1.5 1.5 0 010-3zm10 0a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM4.5 2a1 1 0 01.608.206l.1.087L7.914 5H20.49a1 1 0 01.92 1.39L18.7 12.782A2 2 0 0116.858 14H8v1l.007.117a1 1 0 00.876.876L9 16h9l.117.007a1 1 0 010 1.986L18 18H9l-.176-.005a3 3 0 01-2.819-2.819L6 15v-2l.007-.117a.993.993 0 01.08-.293A2.018 2.018 0 016 12V5.915L4.085 4H3a1 1 0 010-2h1.5zm14.478 5H8v5h8.858l2.12-5z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default CartIcon;
