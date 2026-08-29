import { SvgIcon, SvgIconProps } from '@mui/material';

const CustomersIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M17 11a5.994 5.994 0 015 2.682V17h-8l-.001 1.952.022 1.048H2v-1a6 6 0 019.666-4.748A5.996 5.996 0 0117 11zm-9 4a4.002 4.002 0 00-3.769 2.657l-.062.188-.043.155h7.747l-.037-.137a4.005 4.005 0 00-3.43-2.843l-.206-.015L8 15zm9-2a3.997 3.997 0 00-3.34 1.797l-.125.203h6.929l-.093-.155a4 4 0 00-3.17-1.84L17 13zM8 4a4 4 0 110 8 4 4 0 010-8zm9 0a3 3 0 110 6 3 3 0 010-6zM8 6a2 2 0 100 4 2 2 0 000-4zm9 0h-1v2h2V7 6h-1z"
        clipRule="evenodd"
      ></path>{' '}
    </SvgIcon>
  );
};

export default CustomersIcon;
