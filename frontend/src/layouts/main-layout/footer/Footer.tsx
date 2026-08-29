import { Box, Container, Stack, Typography } from '@mui/material';

const Footer = () => {
  return (
    <Box component="footer" py={2}>
      <Container>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', sm: 'center' }}
          spacing={1}
        >
          <Typography variant="body2" color="text.secondary">
            © {new Date().getFullYear()} AIOps Sentinel
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Intelligent IT Infrastructure Supervision Platform
          </Typography>
        </Stack>
      </Container>
    </Box>
  );
};

export default Footer;
